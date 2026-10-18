package bms.player.beatoraja;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.badlogic.gdx.graphics.Pixmap;

import bms.player.beatoraja.PixmapResourcePool.PixmapResource;

/**
 * Disk cache of decoded skin images.
 * With large skins, screen changes are dominated by PNG decoding (4096px bitmap font pages, effect
 * sprite sheets) whenever the in-memory pixmap cache no longer holds an image. The decoded pixels are
 * stored losslessly compressed in independent chunks, so they are read back on all cores an order of
 * magnitude faster than PNG decoding. Nothing stays in the process: the OS page cache holds recently
 * read files and drops them under memory pressure.
 * Measured on IIDX-light vanilla (150 images, 6.7GB decoded): PNG decoding 5.1s, LZ4 read back 0.12s
 * at 12% of the decoded size.
 */
public final class DecodedImageCache {

	private static final int MAGIC = 0x32445242; // "BRD2"
	private static final int HEADER_BYTES = 4 + 8 + 8 + 4 * 11;
	private static final String SUFFIX = ".px";
	/** Smaller images decode about as fast as they read back, so they are not worth a file. */
	private static final long MIN_BYTES = 1L << 20;
	private static final int CHUNK_BYTES = 1 << 20;

	private static final int CODEC_LZ4 = 1;
	private static final int CODEC_DEFLATE = 2;
	/** compression_algorithm COMPRESSION_LZ4 of macOS libcompression */
	private static final int COMPRESSION_LZ4 = 0x100;
	private static final MethodHandle LZ4_ENCODE;
	private static final MethodHandle LZ4_DECODE;

	static {
		MethodHandle encode = null;
		MethodHandle decode = null;
		try {
			final Linker linker = Linker.nativeLinker();
			final SymbolLookup lib = SymbolLookup.libraryLookup("/usr/lib/libcompression.dylib", Arena.global());
			final FunctionDescriptor fd = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
					ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
			encode = linker.downcallHandle(lib.find("compression_encode_buffer").orElseThrow(), fd);
			decode = linker.downcallHandle(lib.find("compression_decode_buffer").orElseThrow(), fd);
		} catch (Throwable e) {
			// macOS以外ではDeflateを使う
			encode = null;
			decode = null;
		}
		LZ4_ENCODE = encode;
		LZ4_DECODE = decode;
	}

	private final Path directory;
	private final long maxBytes;
	private final int codec = LZ4_ENCODE != null ? CODEC_LZ4 : CODEC_DEFLATE;
	private final ExecutorService workers = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
		final Thread t = new Thread(r, "Decoded image cache worker");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService trimmer = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "Decoded image cache trimmer");
		t.setDaemon(true);
		return t;
	});
	private final AtomicBoolean trimPending = new AtomicBoolean();

	/**
	 * @param directory 保存先
	 * @param maxBytes 保存するファイルの合計サイズ上限(byte)。超過分は最後に使われた時刻が古いものから削除する
	 */
	public DecodedImageCache(Path directory, long maxBytes) {
		this.directory = directory;
		this.maxBytes = maxBytes;
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			Logger.getGlobal().warning("Decoded image cache disabled: " + e.getMessage());
		}
		scheduleTrim();
	}

	/** ~/Library/Caches on macOS, so the files are excluded from backups and may be purged by the system. */
	public static Path defaultDirectory() {
		if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
			return Path.of(System.getProperty("user.home"), "Library", "Caches", "beatoraja", "skin-images");
		}
		return Path.of("cache", "skin-images");
	}

	/**
	 * Returns the image at the path from the cache, or decodes it with the decoder and caches it.
	 * Entries are keyed by path and validated against the source file's size and modification time.
	 */
	public PixmapResource load(String path, Function<String, PixmapResource> decoder) {
		final Path source;
		final BasicFileAttributes attributes;
		try {
			source = Path.of(path).toAbsolutePath().normalize();
			attributes = Files.readAttributes(source, BasicFileAttributes.class);
		} catch (IOException | InvalidPathException e) {
			return decoder.apply(path);
		}
		if (!attributes.isRegularFile()) {
			return decoder.apply(path);
		}
		final long size = attributes.size();
		final long modified = attributes.lastModifiedTime().toMillis();
		final Path file = directory.resolve(key(source.toString()));

		final PixmapResource cached = read(file, size, modified);
		if (cached != null) {
			return cached;
		}
		final PixmapResource decoded = decoder.apply(path);
		if (decoded != null && decoded.getPixmap() != null && decoded.getPixmap().getPixels().capacity() >= MIN_BYTES) {
			write(file, size, modified, decoded);
		}
		return decoded;
	}

	private PixmapResource read(Path file, long size, long modified) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		Pixmap pixmap = null;
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ); Arena arena = Arena.ofShared()) {
			final MemorySegment data = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
			final ByteBuffer header = data.asSlice(0, HEADER_BYTES).asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
			if (header.getInt() != MAGIC || header.getLong() != size || header.getLong() != modified) {
				return null;
			}
			final int width = header.getInt();
			final int height = header.getInt();
			final int format = header.getInt();
			final int frameCount = header.getInt();
			final int frameWidth = header.getInt();
			final int frameHeight = header.getInt();
			final int columns = header.getInt();
			final int rows = header.getInt();
			final int fileCodec = header.getInt();
			final int chunkBytes = header.getInt();
			final int chunks = header.getInt();
			if ((fileCodec == CODEC_LZ4 && LZ4_DECODE == null) || (fileCodec != CODEC_LZ4 && fileCodec != CODEC_DEFLATE)) {
				return null;
			}
			// mmapした画素領域: 解放時にOSへ返る(mallocの大きなブロックは解放後もプロセスに残る)
			pixmap = MappedPixmap.create(width, height, format);
			if (pixmap == null) {
				pixmap = new Pixmap(width, height, Pixmap.Format.fromGdx2DPixmapFormat(format));
			}
			final MemorySegment pixels = MemorySegment.ofBuffer(pixmap.getPixels().duplicate().clear());
			if (chunks != (pixels.byteSize() + chunkBytes - 1) / chunkBytes) {
				pixmap.dispose();
				return null;
			}
			// チャンク表: 各チャンクの圧縮後サイズ。負の値は無圧縮で格納したチャンク
			final long[] offsets = new long[chunks];
			final int[] lengths = new int[chunks];
			long offset = HEADER_BYTES + 4L * chunks;
			for (int i = 0; i < chunks; i++) {
				lengths[i] = data.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), HEADER_BYTES + 4L * i);
				offsets[i] = offset;
				offset += Math.abs(lengths[i]);
			}
			if (offset != data.byteSize()) {
				pixmap.dispose();
				return null;
			}
			final List<Callable<Boolean>> tasks = new ArrayList<>(chunks);
			for (int i = 0; i < chunks; i++) {
				final MemorySegment src = data.asSlice(offsets[i], Math.abs(lengths[i]));
				final long start = (long) i * chunkBytes;
				final MemorySegment dst = pixels.asSlice(start, Math.min(chunkBytes, pixels.byteSize() - start));
				final boolean stored = lengths[i] < 0;
				tasks.add(() -> stored ? copy(src, dst) : decompress(fileCodec, src, dst));
			}
			for (Future<Boolean> f : workers.invokeAll(tasks)) {
				if (!f.get()) {
					pixmap.dispose();
					return null;
				}
			}
			final PixmapResource resource = new PixmapResource(pixmap, frameCount, frameWidth, frameHeight, columns, rows);
			pixmap = null;
			// 最終使用時刻。容量超過時の削除順に使う
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
			return resource;
		} catch (IOException | RuntimeException | InterruptedException | ExecutionException e) {
			if (pixmap != null) {
				pixmap.dispose();
			}
			return null;
		}
	}

	private void write(Path file, long size, long modified, PixmapResource resource) {
		final Pixmap pixmap = resource.getPixmap();
		final Path temp = file.resolveSibling(file.getFileName() + "." + Thread.currentThread().threadId() + ".tmp");
		try (Arena arena = Arena.ofShared()) {
			final MemorySegment pixels = MemorySegment.ofBuffer(pixmap.getPixels().duplicate().clear());
			final int chunks = (int) ((pixels.byteSize() + CHUNK_BYTES - 1) / CHUNK_BYTES);
			final List<Callable<MemorySegment>> tasks = new ArrayList<>(chunks);
			for (int i = 0; i < chunks; i++) {
				final long start = (long) i * CHUNK_BYTES;
				final MemorySegment src = pixels.asSlice(start, Math.min(CHUNK_BYTES, pixels.byteSize() - start));
				tasks.add(() -> compress(src, arena));
			}
			final List<MemorySegment> compressed = new ArrayList<>(chunks);
			for (Future<MemorySegment> f : workers.invokeAll(tasks)) {
				compressed.add(f.get());
			}

			final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES + 4 * chunks).order(ByteOrder.LITTLE_ENDIAN);
			header.putInt(MAGIC).putLong(size).putLong(modified)
					.putInt(pixmap.getWidth()).putInt(pixmap.getHeight())
					.putInt(Pixmap.Format.toGdx2DPixmapFormat(pixmap.getFormat()))
					.putInt(resource.getFrameCount()).putInt(resource.getFrameWidth()).putInt(resource.getFrameHeight())
					.putInt(resource.getColumns()).putInt(resource.getRows())
					.putInt(codec).putInt(CHUNK_BYTES).putInt(chunks);
			for (int i = 0; i < chunks; i++) {
				final MemorySegment c = compressed.get(i);
				// 圧縮できなかったチャンクは元データを負のサイズで格納する
				header.putInt(c != null ? (int) c.byteSize() : -(int) chunkSize(pixels, i));
			}
			header.flip();
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING)) {
				writeFully(channel, header);
				for (int i = 0; i < chunks; i++) {
					final MemorySegment c = compressed.get(i);
					final long start = (long) i * CHUNK_BYTES;
					writeFully(channel, (c != null ? c : pixels.asSlice(start, chunkSize(pixels, i))).asByteBuffer());
				}
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			scheduleTrim();
		} catch (IOException | RuntimeException | InterruptedException | ExecutionException e) {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
			}
		}
	}

	private static long chunkSize(MemorySegment pixels, int chunk) {
		return Math.min(CHUNK_BYTES, pixels.byteSize() - (long) chunk * CHUNK_BYTES);
	}

	/** @return 圧縮後のデータ。元より小さくならない場合はnull */
	private MemorySegment compress(MemorySegment src, Arena arena) {
		if (codec == CODEC_LZ4) {
			final MemorySegment dst = arena.allocate(src.byteSize());
			final long length = lz4(LZ4_ENCODE, src, dst);
			return length > 0 ? dst.asSlice(0, length) : null;
		}
		final Deflater deflater = new Deflater(Deflater.BEST_SPEED, true);
		try {
			deflater.setInput(src.asByteBuffer());
			deflater.finish();
			final MemorySegment dst = arena.allocate(src.byteSize());
			final int length = deflater.deflate(dst.asByteBuffer());
			return deflater.finished() ? dst.asSlice(0, length) : null;
		} finally {
			deflater.end();
		}
	}

	private static boolean decompress(int codec, MemorySegment src, MemorySegment dst) {
		if (codec == CODEC_LZ4) {
			return lz4(LZ4_DECODE, src, dst) == dst.byteSize();
		}
		final Inflater inflater = new Inflater(true);
		try {
			inflater.setInput(src.asByteBuffer());
			return inflater.inflate(dst.asByteBuffer()) == dst.byteSize();
		} catch (DataFormatException e) {
			return false;
		} finally {
			inflater.end();
		}
	}

	/** compression_encode_buffer / compression_decode_buffer: srcの内容をdstに書き込み、書き込んだサイズを返す。失敗時は0 */
	private static long lz4(MethodHandle function, MemorySegment src, MemorySegment dst) {
		try {
			return (long) function.invokeExact(dst, dst.byteSize(), src, src.byteSize(), MemorySegment.NULL, COMPRESSION_LZ4);
		} catch (Throwable e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean copy(MemorySegment src, MemorySegment dst) {
		if (src.byteSize() != dst.byteSize()) {
			return false;
		}
		dst.copyFrom(src);
		return true;
	}

	private void scheduleTrim() {
		if (trimPending.compareAndSet(false, true)) {
			trimmer.execute(() -> {
				trimPending.set(false);
				trim();
			});
		}
	}

	/** Deletes the least recently used files while the total exceeds maxBytes. */
	private void trim() {
		record Entry(Path path, long size, long used) {}
		final List<Entry> entries = new ArrayList<>();
		long total = 0;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
			for (Path p : files) {
				final String name = p.getFileName().toString();
				final BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
				if (name.endsWith(".tmp") && a.lastModifiedTime().toMillis() < System.currentTimeMillis() - 3600_000L) {
					// 書き込み途中で終了した残骸
					Files.deleteIfExists(p);
				} else if (name.endsWith(SUFFIX)) {
					entries.add(new Entry(p, a.size(), a.lastModifiedTime().toMillis()));
					total += a.size();
				}
			}
		} catch (IOException e) {
			return;
		}
		if (total <= maxBytes) {
			return;
		}
		entries.sort((a, b) -> Long.compare(a.used(), b.used()));
		for (Entry e : entries) {
			if (total <= maxBytes) {
				break;
			}
			try {
				Files.deleteIfExists(e.path());
				total -= e.size();
			} catch (IOException ignored) {
			}
		}
	}

	private static String key(String path) {
		try {
			final byte[] digest = MessageDigest.getInstance("SHA-1").digest(path.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest) + SUFFIX;
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			channel.write(buffer);
		}
	}
}
