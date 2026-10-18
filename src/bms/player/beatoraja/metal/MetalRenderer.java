package bms.player.beatoraja.metal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.Buffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.lwjgl.glfw.GLFWNativeCocoa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import bms.player.beatoraja.ShaderManager;

/**
 * Draws the game with Metal (macos/metal/MetalRenderer.m) instead of Apple's OpenGL.
 * <p>
 * Apple's OpenGL keeps two copies of every texture and can only upload on the render thread. Here every texture
 * is a single MTLTexture that can also be filled on a background thread ({@link MetalTextureLoader}).
 * libGDX keeps its GL context for bookkeeping (shader objects, texture and buffer names), but {@link MetalGL20}
 * turns texture uploads, framebuffers, clears and pixel reads into Metal calls, and {@link MetalSpriteBatch}
 * sends each batch of quads to Metal. Nothing is drawn with GL.
 */
public final class MetalRenderer {

	// Shader and texture kind ids shared with MetalRenderer.m
	static final int SHADER_DEFAULT = 0, SHADER_BILINEAR = 1, SHADER_FFMPEG = 2, SHADER_LAYER = 3, SHADER_DISTANCE_FIELD = 4;
	static final int KIND_RGBA = 0, KIND_ALPHA = 1, KIND_LUMINANCE_ALPHA = 2, KIND_LUMINANCE = 3, KIND_TARGET = 4;

	private static MethodHandle init, textureCreate, textureUpload, textureMipmapped, textureRelease, beginFrame, setTarget,
			clear, draw, readPixels, endFrame;

	private static boolean active;
	private static boolean lowResolution;
	private static boolean frameOpen;
	private static int width, height;
	private static MetalGL20 gl;

	private static final Arena arena = Arena.ofAuto();
	private static MemorySegment vertexData, matrixData, paramData, uniformData;
	private static final int[] params = new int[20];
	private static final float[] uniforms = new float[12];
	private static final Map<ShaderProgram, Integer> shaders = new IdentityHashMap<>();

	private MetalRenderer() {
	}

	public static boolean isActive() {
		return active;
	}

	/**
	 * Puts a Metal layer over the window and routes drawing to it. Call from ApplicationListener.create.
	 *
	 * @param lowResolution draw at 1 pixel per point on Retina displays and let Core Animation upscale
	 * @return false if Metal is unavailable; GL keeps drawing
	 */
	public static boolean init(boolean vsync, boolean lowResolution) {
		try {
			System.loadLibrary("beatoraja_metal");
			final Linker linker = Linker.nativeLinker();
			final SymbolLookup lib = SymbolLookup.loaderLookup();
			final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
			final ValueLayout.OfFloat F = ValueLayout.JAVA_FLOAT;
			final var A = ValueLayout.ADDRESS;
			init = linker.downcallHandle(lib.find("bm_init").orElseThrow(), FunctionDescriptor.of(I, A, I, I));
			textureCreate = linker.downcallHandle(lib.find("bm_texture_create").orElseThrow(), FunctionDescriptor.of(A, I, I, I, I));
			textureUpload = linker.downcallHandle(lib.find("bm_texture_upload").orElseThrow(), FunctionDescriptor.ofVoid(A, I, I, I, I, I, I, A));
			textureMipmapped = linker.downcallHandle(lib.find("bm_texture_mipmapped").orElseThrow(), FunctionDescriptor.of(A, A, I));
			textureRelease = linker.downcallHandle(lib.find("bm_texture_release").orElseThrow(), FunctionDescriptor.ofVoid(A));
			beginFrame = linker.downcallHandle(lib.find("bm_begin_frame").orElseThrow(), FunctionDescriptor.of(I, I, I));
			setTarget = linker.downcallHandle(lib.find("bm_set_target").orElseThrow(), FunctionDescriptor.ofVoid(A));
			clear = linker.downcallHandle(lib.find("bm_clear").orElseThrow(), FunctionDescriptor.ofVoid(F, F, F, F));
			draw = linker.downcallHandle(lib.find("bm_draw").orElseThrow(), FunctionDescriptor.ofVoid(A, I, A, A, A, A));
			readPixels = linker.downcallHandle(lib.find("bm_read_pixels").orElseThrow(), FunctionDescriptor.of(I, I, I, I, I, A));
			endFrame = linker.downcallHandle(lib.find("bm_end_frame").orElseThrow(), FunctionDescriptor.ofVoid());

			final Lwjgl3Graphics graphics = (Lwjgl3Graphics) Gdx.graphics;
			final long window = GLFWNativeCocoa.glfwGetCocoaWindow(graphics.getWindow().getWindowHandle());
			final boolean nearest = lowResolution && graphics.getBackBufferWidth() % Math.max(1, graphics.getWidth()) == 0;
			if ((int) init.invokeExact(MemorySegment.ofAddress(window), vsync ? 1 : 0, nearest ? 1 : 0) == 0) {
				Logger.getGlobal().warning("Metalを初期化できませんでした。OpenGLで描画します");
				return false;
			}
			vertexData = arena.allocate(8191L * 20 * Float.BYTES, 16);
			matrixData = arena.allocate(16 * Float.BYTES, 16);
			paramData = arena.allocate(params.length * Integer.BYTES, 16);
			uniformData = arena.allocate(uniforms.length * Float.BYTES, 16);

			gl = new MetalGL20(graphics.getGL20());
			graphics.setGL20(gl);
			Gdx.gl = gl;
			Gdx.gl20 = gl;
			MetalRenderer.lowResolution = lowResolution;
			active = true;
			Logger.getGlobal().info("Metalで描画します");
			return true;
		} catch (Throwable e) {
			Logger.getGlobal().warning("Metalを初期化できませんでした。OpenGLで描画します : " + e);
			return false;
		}
	}

	static MetalGL20 gl() {
		return gl;
	}

	/** A SpriteBatch that draws with Metal while it is active. */
	public static SpriteBatch newSpriteBatch() {
		return active ? new MetalSpriteBatch() : new SpriteBatch();
	}

	/** Drawable size: points when drawing at low resolution, pixels otherwise */
	public static int getWidth() {
		return width;
	}

	public static int getHeight() {
		return height;
	}

	public static void beginFrame() {
		if (!active) {
			return;
		}
		final boolean low = lowResolution && Gdx.graphics.getBackBufferWidth() > Gdx.graphics.getWidth();
		width = low ? Gdx.graphics.getWidth() : Gdx.graphics.getBackBufferWidth();
		height = low ? Gdx.graphics.getHeight() : Gdx.graphics.getBackBufferHeight();
		// Viewport and scissor calls from skins and scene2d are in points, which then equal drawable pixels.
		HdpiUtils.setMode(low ? HdpiMode.Pixels : HdpiMode.Logical);
		gl.beginFrame();
		try {
			frameOpen = (int) beginFrame.invokeExact(width, height) != 0;
		} catch (Throwable e) {
			failed(e);
			frameOpen = false;
		}
	}

	public static void endFrame() {
		if (!active || !frameOpen) {
			return;
		}
		frameOpen = false;
		try {
			endFrame.invokeExact();
		} catch (Throwable e) {
			failed(e);
		}
	}

	/** One SpriteBatch flush: count floats of vertices, all quads with the same texture and state. */
	static void draw(float[] vertices, int count, Texture texture, float[] matrix, ShaderProgram shader, boolean blend,
			int src, int dst, int srcAlpha, int dstAlpha) {
		if (!frameOpen || count <= 0) {
			return;
		}
		final MetalTexture t = gl.texture(texture.getTextureObjectHandle());
		if (t == null || t.handle == null) {
			return;
		}
		final int shaderId = shaderId(shader);
		params[0] = shaderId;
		params[1] = blend ? 1 : 0;
		params[2] = src;
		params[3] = dst;
		params[4] = srcAlpha;
		params[5] = dstAlpha;
		params[6] = t.minFilter;
		params[7] = t.magFilter;
		params[8] = t.wrapS;
		params[9] = t.wrapT;
		gl.scissorAndViewport(params, 10);
		if (shaderId == SHADER_DISTANCE_FIELD) {
			gl.distanceFieldUniforms(shader.getHandle(), uniforms);
			MemorySegment.copy(uniforms, 0, uniformData, ValueLayout.JAVA_FLOAT, 0, uniforms.length);
		}
		MemorySegment.copy(vertices, 0, vertexData, ValueLayout.JAVA_FLOAT, 0, count);
		MemorySegment.copy(matrix, 0, matrixData, ValueLayout.JAVA_FLOAT, 0, 16);
		MemorySegment.copy(params, 0, paramData, ValueLayout.JAVA_INT, 0, params.length);
		try {
			draw.invokeExact(vertexData, count / 20, t.handle, matrixData, paramData, uniformData);
		} catch (Throwable e) {
			failed(e);
		}
	}

	private static int shaderId(ShaderProgram shader) {
		if (shaders.isEmpty()) {
			shaders.put(ShaderManager.getShader("bilinear"), SHADER_BILINEAR);
			shaders.put(ShaderManager.getShader("ffmpeg"), SHADER_FFMPEG);
			shaders.put(ShaderManager.getShader("layer"), SHADER_LAYER);
			shaders.put(ShaderManager.getShader("distance_field"), SHADER_DISTANCE_FIELD);
		}
		final Integer id = shaders.get(shader);
		return id != null ? id : SHADER_DEFAULT;
	}

	private static boolean reported;

	/** A native call that throws is a bug in this class; report it once instead of every frame. */
	private static void failed(Throwable e) {
		if (!reported) {
			reported = true;
			Logger.getGlobal().log(java.util.logging.Level.SEVERE, "Metal呼び出しに失敗しました", e);
		}
	}

	// ---- natives used by MetalGL20 and MetalTextureLoader ----

	static int kindOf(int glFormat) {
		return switch (glFormat) {
			case GL20.GL_ALPHA -> KIND_ALPHA;
			case GL20.GL_LUMINANCE_ALPHA -> KIND_LUMINANCE_ALPHA;
			case GL20.GL_LUMINANCE -> KIND_LUMINANCE;
			default -> KIND_RGBA;
		};
	}

	/** Thread safe */
	static MemorySegment createTexture(int width, int height, int kind) {
		try {
			final MemorySegment texture = (MemorySegment) textureCreate.invokeExact(width, height, kind, 0);
			return texture.address() != 0 ? texture : null;
		} catch (Throwable e) {
			failed(e);
			return null;
		}
	}

	/** Thread safe. pixels must be native memory. */
	static void uploadTexture(MemorySegment texture, int x, int y, int width, int height, int glFormat, int glType, MemorySegment pixels) {
		try {
			textureUpload.invokeExact(texture, x, y, width, height, glFormat, glType, pixels);
		} catch (Throwable e) {
			failed(e);
		}
	}

	static void uploadTexture(MemorySegment texture, int x, int y, int width, int height, int glFormat, int glType, Buffer pixels) {
		final MemorySegment segment = MemorySegment.ofBuffer(pixels);
		if (segment.isNative()) {
			uploadTexture(texture, x, y, width, height, glFormat, glType, segment);
			return;
		}
		try (Arena temp = Arena.ofConfined()) {
			final MemorySegment copy = temp.allocate(segment.byteSize());
			copy.copyFrom(segment);
			uploadTexture(texture, x, y, width, height, glFormat, glType, copy);
		}
	}

	static MemorySegment mipmapped(MemorySegment texture, int kind) {
		try {
			return (MemorySegment) textureMipmapped.invokeExact(texture, kind);
		} catch (Throwable e) {
			failed(e);
			return null;
		}
	}

	/**
	 * Freeing a large texture returns its pages to the system and takes milliseconds, so it happens off the render
	 * thread. Command buffers still using the texture hold their own reference.
	 */
	static void releaseTexture(MemorySegment texture) {
		if (texture == null) {
			return;
		}
		releaser.execute(() -> {
			try {
				textureRelease.invokeExact(texture);
			} catch (Throwable e) {
				failed(e);
			}
		});
	}

	private static final ExecutorService releaser = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "metal-texture-release");
		t.setDaemon(true);
		return t;
	});

	static void setTarget(MemorySegment texture) {
		if (!frameOpen) {
			return;
		}
		// invokeExact needs the exact static type; a conditional expression would be Object
		final MemorySegment target = texture != null ? texture : MemorySegment.NULL;
		try {
			setTarget.invokeExact(target);
		} catch (Throwable e) {
			failed(e);
		}
	}

	static void clear(float r, float g, float b, float a) {
		if (!frameOpen) {
			return;
		}
		try {
			clear.invokeExact(r, g, b, a);
		} catch (Throwable e) {
			failed(e);
		}
	}

	static boolean readPixels(int x, int y, int width, int height, Buffer pixels) {
		if (!frameOpen) {
			return false;
		}
		final MemorySegment segment = MemorySegment.ofBuffer(pixels);
		try {
			if (segment.isNative()) {
				return (int) readPixels.invokeExact(x, y, width, height, segment) != 0;
			}
			try (Arena temp = Arena.ofConfined()) {
				final MemorySegment copy = temp.allocate((long) width * height * 4);
				final boolean ok = (int) readPixels.invokeExact(x, y, width, height, copy) != 0;
				segment.copyFrom(copy.asSlice(0, Math.min(copy.byteSize(), segment.byteSize())));
				return ok;
			}
		} catch (Throwable e) {
			failed(e);
			return false;
		}
	}
}
