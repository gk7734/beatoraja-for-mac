package bms.player.beatoraja.metal;

import java.lang.foreign.MemorySegment;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.IntMap;

/**
 * GL20 seen by libGDX while Metal draws. Texture data, framebuffers, clears, scissor, viewport and pixel reads go
 * to Metal; GL objects are still created so libGDX's handles stay valid, but GL never stores texture pixels or
 * draws anything. Render thread only.
 */
final class MetalGL20 extends GL20Delegate {

	private final IntMap<MetalTexture> textures = new IntMap<>();
	private final int[] bound = new int[32];
	private int unit;

	/** framebuffer name -> colour attachment texture name */
	private final IntMap<Integer> framebuffers = new IntMap<>();
	private int framebuffer;

	private boolean scissorEnabled;
	private final int[] scissor = new int[4];
	private boolean viewportSet;
	private final int[] viewport = new int[4];
	private final float[] clearColor = { 0, 0, 0, 0 };

	private int program;
	/** (program, location) -> uniform name */
	private final Map<Long, String> uniformNames = new HashMap<>();
	/** program -> uniform name -> value; distance field fonts read theirs at draw time */
	private final Map<Integer, Map<String, float[]>> uniformValues = new HashMap<>();

	MetalGL20(GL20 gl) {
		super(gl);
	}

	MetalTexture texture(int name) {
		return textures.get(name);
	}

	void beginFrame() {
		framebuffer = 0;
		viewportSet = false;
	}

	/** Writes scissor (on, x, y, w, h) and viewport (set, x, y, w, h) in GL coordinates. */
	void scissorAndViewport(int[] p, int offset) {
		p[offset] = scissorEnabled ? 1 : 0;
		System.arraycopy(scissor, 0, p, offset + 1, 4);
		p[offset + 5] = viewportSet ? 1 : 0;
		System.arraycopy(viewport, 0, p, offset + 6, 4);
	}

	/** glsl/distance_field.frag uniforms: outlineDistance, shadowSmoothing, shadowOffset, outlineColor, shadowColor */
	void distanceFieldUniforms(int program, float[] out) {
		final Map<String, float[]> u = uniformValues.getOrDefault(program, Map.of());
		out[0] = value(u, "u_outlineDistance", 0, 0.5f);
		out[1] = value(u, "u_shadowSmoothing", 0, 0f);
		out[2] = value(u, "u_shadowOffset", 0, 0f);
		out[3] = value(u, "u_shadowOffset", 1, 0f);
		for (int i = 0; i < 4; i++) {
			out[4 + i] = value(u, "u_outlineColor", i, 0f);
			out[8 + i] = value(u, "u_shadowColor", i, 0f);
		}
	}

	private static float value(Map<String, float[]> u, String name, int index, float fallback) {
		final float[] v = u.get(name);
		return v != null && v.length > index ? v[index] : fallback;
	}

	// ---- textures ----

	@Override
	public void glActiveTexture(int texture) {
		unit = (texture - GL20.GL_TEXTURE0) & 31;
	}

	@Override
	public void glBindTexture(int target, int texture) {
		bound[unit] = texture;
	}

	@Override
	public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type,
			Buffer pixels) {
		// Mipmap levels come from glGenerateMipmap; libGDX only uploads level 0 itself.
		if (target != GL20.GL_TEXTURE_2D || level != 0) {
			return;
		}
		final int name = bound[unit];
		MetalTexture t = textures.get(name);
		if (t == null) {
			t = new MetalTexture();
			textures.put(name, t);
		}
		final int kind = MetalRenderer.kindOf(format);
		if (t.handle == null || t.width != width || t.height != height || t.kind != kind) {
			t.replace(MetalRenderer.createTexture(width, height, kind), kind, width, height);
		}
		if (pixels != null && t.handle != null) {
			MetalRenderer.uploadTexture(t.handle, 0, 0, width, height, format, type, pixels);
		}
	}

	@Override
	public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type,
			Buffer pixels) {
		final MetalTexture t = textures.get(bound[unit]);
		if (target == GL20.GL_TEXTURE_2D && level == 0 && t != null && t.handle != null && pixels != null) {
			MetalRenderer.uploadTexture(t.handle, xoffset, yoffset, width, height, format, type, pixels);
		}
	}

	@Override
	public void glGenerateMipmap(int target) {
		final MetalTexture t = textures.get(bound[unit]);
		if (t != null && t.handle != null && t.kind != MetalRenderer.KIND_TARGET) {
			final MemorySegment mipmapped = MetalRenderer.mipmapped(t.handle, t.kind);
			if (mipmapped != null) {
				t.replace(mipmapped, t.kind, t.width, t.height);
			}
		}
	}

	@Override
	public void glTexParameteri(int target, int pname, int param) {
		final MetalTexture t = textures.get(bound[unit]);
		if (t == null) {
			return;
		}
		switch (pname) {
			case GL20.GL_TEXTURE_MIN_FILTER -> t.minFilter = param;
			case GL20.GL_TEXTURE_MAG_FILTER -> t.magFilter = param;
			case GL20.GL_TEXTURE_WRAP_S -> t.wrapS = param;
			case GL20.GL_TEXTURE_WRAP_T -> t.wrapT = param;
		}
	}

	@Override
	public void glTexParameterf(int target, int pname, float param) {
		glTexParameteri(target, pname, (int) param);
	}

	@Override
	public void glDeleteTexture(int texture) {
		final MetalTexture t = textures.remove(texture);
		if (t != null) {
			t.release();
		}
		super.glDeleteTexture(texture);
	}

	@Override
	public void glDeleteTextures(int n, IntBuffer textures) {
		for (int i = 0; i < n; i++) {
			final MetalTexture t = this.textures.remove(textures.get(textures.position() + i));
			if (t != null) {
				t.release();
			}
		}
		super.glDeleteTextures(n, textures);
	}

	@Override
	public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) {
	}

	@Override
	public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
	}

	// ---- framebuffers ----

	@Override
	public void glBindFramebuffer(int target, int framebuffer) {
		this.framebuffer = framebuffer;
		final Integer texture = framebuffer != 0 ? framebuffers.get(framebuffer) : null;
		final MetalTexture t = texture != null ? textures.get(texture) : null;
		MetalRenderer.setTarget(t != null ? t.handle : null);
		viewportSet = false;
	}

	@Override
	public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
		if (attachment != GL20.GL_COLOR_ATTACHMENT0) {
			return;
		}
		framebuffers.put(framebuffer, texture);
		final MetalTexture t = textures.get(texture);
		if (t != null && t.kind != MetalRenderer.KIND_TARGET) {
			t.replace(MetalRenderer.createTexture(t.width, t.height, MetalRenderer.KIND_TARGET), MetalRenderer.KIND_TARGET, t.width, t.height);
		}
		MetalRenderer.setTarget(t != null ? t.handle : null);
	}

	@Override
	public int glCheckFramebufferStatus(int target) {
		return GL20.GL_FRAMEBUFFER_COMPLETE;
	}

	@Override
	public void glDeleteFramebuffer(int framebuffer) {
		framebuffers.remove(framebuffer);
		super.glDeleteFramebuffer(framebuffer);
	}

	@Override
	public void glDeleteFramebuffers(int n, IntBuffer framebuffers) {
		for (int i = 0; i < n; i++) {
			this.framebuffers.remove(framebuffers.get(framebuffers.position() + i));
		}
		super.glDeleteFramebuffers(n, framebuffers);
	}

	// ---- per-draw state ----

	@Override
	public void glEnable(int cap) {
		if (cap == GL20.GL_SCISSOR_TEST) {
			scissorEnabled = true;
		}
	}

	@Override
	public void glDisable(int cap) {
		if (cap == GL20.GL_SCISSOR_TEST) {
			scissorEnabled = false;
		}
	}

	@Override
	public void glScissor(int x, int y, int width, int height) {
		scissor[0] = x;
		scissor[1] = y;
		scissor[2] = width;
		scissor[3] = height;
	}

	@Override
	public void glViewport(int x, int y, int width, int height) {
		viewport[0] = x;
		viewport[1] = y;
		viewport[2] = width;
		viewport[3] = height;
		viewportSet = true;
	}

	@Override
	public void glClearColor(float red, float green, float blue, float alpha) {
		clearColor[0] = red;
		clearColor[1] = green;
		clearColor[2] = blue;
		clearColor[3] = alpha;
	}

	@Override
	public void glClear(int mask) {
		if ((mask & GL20.GL_COLOR_BUFFER_BIT) != 0) {
			MetalRenderer.clear(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
		}
	}

	@Override
	public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels) {
		if (framebuffer != 0 || format != GL20.GL_RGBA || type != GL20.GL_UNSIGNED_BYTE
				|| !MetalRenderer.readPixels(x, y, width, height, pixels)) {
			super.glReadPixels(x, y, width, height, format, type, pixels);
		}
	}

	@Override
	public void glDrawArrays(int mode, int first, int count) {
	}

	@Override
	public void glDrawElements(int mode, int count, int type, Buffer indices) {
	}

	@Override
	public void glDrawElements(int mode, int count, int type, int indices) {
	}

	// ---- uniforms (distance field fonts) ----

	@Override
	public void glUseProgram(int program) {
		this.program = program;
		super.glUseProgram(program);
	}

	@Override
	public int glGetUniformLocation(int program, String name) {
		final int location = super.glGetUniformLocation(program, name);
		uniformNames.put(((long) program << 32) | (location & 0xffffffffL), name);
		return location;
	}

	@Override
	public void glUniform1f(int location, float x) {
		uniform(location, new float[] { x });
		super.glUniform1f(location, x);
	}

	@Override
	public void glUniform2f(int location, float x, float y) {
		uniform(location, new float[] { x, y });
		super.glUniform2f(location, x, y);
	}

	@Override
	public void glUniform3f(int location, float x, float y, float z) {
		uniform(location, new float[] { x, y, z });
		super.glUniform3f(location, x, y, z);
	}

	@Override
	public void glUniform4f(int location, float x, float y, float z, float w) {
		uniform(location, new float[] { x, y, z, w });
		super.glUniform4f(location, x, y, z, w);
	}

	private void uniform(int location, float[] value) {
		final String name = uniformNames.get(((long) program << 32) | (location & 0xffffffffL));
		if (name != null) {
			uniformValues.computeIfAbsent(program, k -> new HashMap<>()).put(name, value);
		}
	}
}
