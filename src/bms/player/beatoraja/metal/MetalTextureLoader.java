package bms.player.beatoraja.metal;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.GdxRuntimeException;

import bms.player.beatoraja.DecodedImageCache;
import bms.player.beatoraja.PixmapResourcePool;
import bms.player.beatoraja.PixmapResourcePool.PixmapResource;

/**
 * Loads large skin images off the render thread. The texture is returned at once with its real size but no
 * pixels (it draws nothing); a loader thread reads the image from the decoded-image cache and fills a new
 * MTLTexture, and the render thread only swaps it in. Screen changes no longer wait for multi-megapixel effect
 * sheets that are shown seconds later, if at all.
 */
public final class MetalTextureLoader {

	/** Smaller images load on the render thread as before */
	private static final long MIN_PIXELS = 1024 * 1024;

	private static final ExecutorService loader = Executors.newFixedThreadPool(2, r -> {
		final Thread t = new Thread(r, "metal-texture-loader");
		t.setDaemon(true);
		return t;
	});

	private MetalTextureLoader() {
	}

	/**
	 * @return a texture that fills in later, or null to load the image normally
	 */
	public static Texture load(String path, boolean useMipMaps, PixmapResourcePool pool) {
		final DecodedImageCache cache = pool.getDiskCache();
		if (!MetalRenderer.isActive() || useMipMaps || cache == null || !path.toLowerCase(Locale.ROOT).endsWith(".png")) {
			return null;
		}
		final int[] size = pngSize(path);
		if (size == null || (long) size[0] * size[1] < MIN_PIXELS) {
			return null;
		}
		final Texture texture = new Texture(new EmptyTextureData(size[0], size[1]));
		final int name = texture.getTextureObjectHandle();
		loader.execute(() -> {
			PixmapResource resource = null;
			try {
				resource = cache.load(path, PixmapResourcePool::loadPictureResource);
				final Pixmap pixmap = resource != null ? resource.getPixmap() : null;
				if (pixmap == null || pixmap.getWidth() != size[0] || pixmap.getHeight() != size[1]) {
					Logger.getGlobal().warning("画像を読み込めませんでした : " + path);
					return;
				}
				final int kind = MetalRenderer.kindOf(pixmap.getGLFormat());
				final MemorySegment handle = MetalRenderer.createTexture(size[0], size[1], kind);
				if (handle == null) {
					return;
				}
				MetalRenderer.uploadTexture(handle, 0, 0, size[0], size[1], pixmap.getGLFormat(), pixmap.getGLType(),
						MemorySegment.ofBuffer(pixmap.getPixels()));
				Gdx.app.postRunnable(() -> {
					final MetalTexture t = MetalRenderer.gl().texture(name);
					// Disposed (or its name reused) before the image arrived
					if (t == null || texture.getTextureObjectHandle() != name) {
						MetalRenderer.releaseTexture(handle);
						return;
					}
					t.replace(handle, kind, size[0], size[1]);
				});
			} catch (Throwable e) {
				Logger.getGlobal().warning("画像を読み込めませんでした : " + path + " : " + e);
			} finally {
				if (resource != null) {
					resource.dispose();
				}
			}
		});
		return texture;
	}

	/** Width and height from the PNG IHDR chunk */
	private static int[] pngSize(String path) {
		try (InputStream in = Files.newInputStream(Path.of(path))) {
			final byte[] header = in.readNBytes(24);
			if (header.length < 24 || (header[0] & 0xff) != 0x89 || header[1] != 'P' || header[12] != 'I' || header[13] != 'H') {
				return null;
			}
			final ByteBuffer b = ByteBuffer.wrap(header);
			return new int[] { b.getInt(16), b.getInt(20) };
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Reports the image size for TextureRegion coordinates; holds a transparent 1x1 until the image arrives. */
	private static final class EmptyTextureData implements TextureData {
		private final int width;
		private final int height;

		EmptyTextureData(int width, int height) {
			this.width = width;
			this.height = height;
		}

		@Override
		public TextureDataType getType() {
			return TextureDataType.Custom;
		}

		@Override
		public boolean isPrepared() {
			return true;
		}

		@Override
		public void prepare() {
		}

		@Override
		public Pixmap consumePixmap() {
			throw new GdxRuntimeException("This TextureData does not return a Pixmap");
		}

		@Override
		public boolean disposePixmap() {
			return false;
		}

		@Override
		public void consumeCustomData(int target) {
			Gdx.gl.glTexImage2D(target, 0, GL20.GL_RGBA, 1, 1, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(4));
		}

		@Override
		public int getWidth() {
			return width;
		}

		@Override
		public int getHeight() {
			return height;
		}

		@Override
		public Pixmap.Format getFormat() {
			return Pixmap.Format.RGBA8888;
		}

		@Override
		public boolean useMipMaps() {
			return false;
		}

		@Override
		public boolean isManaged() {
			return false;
		}
	}
}
