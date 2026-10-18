package bms.player.beatoraja;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Gdx2DPixmap;

/**
 * Pixmap whose pixels live in an anonymous mmap instead of malloc. macOS malloc keeps freed large blocks dirty in
 * the process (about 1.4GB after the play screen's images were loaded and released), while munmap returns the
 * pages to the system at once. The gdx2d natives (drawPixmap, getPixel ...) work on it through a gdx2d_pixmap
 * header allocated here: {uint32 width, height, format, blend, scale; const unsigned char *pixels} (32 bytes).
 */
final class MappedPixmap extends Gdx2DPixmap {

	private static final int PROT_READ = 0x1;
	private static final int PROT_WRITE = 0x2;
	private static final int MAP_PRIVATE = 0x2;
	private static final int MAP_ANON = 0x1000;
	/** VM_MAKE_TAG(VM_MEMORY_APPLICATION_SPECIFIC_1): footprint/vmmap show these pages as their own category */
	private static final int VM_TAG = 240 << 24;
	private static final long HEADER_BYTES = 32;
	private static final long PIXELS_OFFSET = 24;

	private static final MethodHandle MMAP;
	private static final MethodHandle MUNMAP;

	static {
		MethodHandle mmap = null;
		MethodHandle munmap = null;
		try {
			final Linker linker = Linker.nativeLinker();
			mmap = linker.downcallHandle(linker.defaultLookup().find("mmap").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
							ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
			munmap = linker.downcallHandle(linker.defaultLookup().find("munmap").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
		} catch (Throwable e) {
			mmap = null;
			munmap = null;
		}
		MMAP = mmap;
		MUNMAP = munmap;
	}

	private final Arena header;
	private final MemorySegment pixels;
	private boolean disposed;

	private MappedPixmap(MemorySegment pixels, Arena header, MemorySegment struct, int width, int height, int format) {
		super(pixels.asByteBuffer(), new long[] { struct.address(), width, height, format });
		this.pixels = pixels;
		this.header = header;
	}

	/**
	 * mmapした領域を画素に使うPixmapを作る
	 *
	 * @param format gdx2dの画素形式
	 * @return 作れなかった場合はnull(呼び出し元は通常のPixmapを使う)
	 */
	static Pixmap create(int width, int height, int format) {
		final long bytes = (long) width * height * bytesPerPixel(format);
		if (MMAP == null || bytes <= 0) {
			return null;
		}
		final MemorySegment pixels;
		try {
			final MemorySegment address = (MemorySegment) MMAP.invokeExact(MemorySegment.NULL, bytes,
					PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, VM_TAG, 0L);
			if (address.address() == -1L || address.address() == 0L) {
				return null;
			}
			pixels = address.reinterpret(bytes);
		} catch (Throwable e) {
			return null;
		}
		final Arena header = Arena.ofShared();
		final MemorySegment struct = header.allocate(HEADER_BYTES, 8);
		struct.set(ValueLayout.JAVA_INT, 0, width);
		struct.set(ValueLayout.JAVA_INT, 4, height);
		struct.set(ValueLayout.JAVA_INT, 8, format);
		struct.set(ValueLayout.JAVA_INT, 12, 0);
		struct.set(ValueLayout.JAVA_INT, 16, 0);
		struct.set(ValueLayout.JAVA_LONG, PIXELS_OFFSET, pixels.address());
		return new Pixmap(new MappedPixmap(pixels, header, struct, width, height, format));
	}

	private static int bytesPerPixel(int format) {
		return switch (format) {
			case GDX2D_FORMAT_ALPHA -> 1;
			case GDX2D_FORMAT_LUMINANCE_ALPHA, GDX2D_FORMAT_RGB565, GDX2D_FORMAT_RGBA4444 -> 2;
			case GDX2D_FORMAT_RGB888 -> 3;
			default -> 4;
		};
	}

	@Override
	public synchronized void dispose() {
		if (disposed) {
			return;
		}
		disposed = true;
		try {
			final int result = (int) MUNMAP.invokeExact(pixels, pixels.byteSize());
		} catch (Throwable ignored) {
		}
		header.close();
	}
}
