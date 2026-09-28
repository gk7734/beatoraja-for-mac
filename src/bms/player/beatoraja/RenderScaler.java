package bms.player.beatoraja;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;

/**
 * Renders frames at 1 pixel per point on Retina displays and upscales once to the backbuffer.
 * GLFW 3.4 on macOS ignores GLFW_SCALE_FRAMEBUFFER, so the backbuffer is always 2x; drawing every
 * skin object there costs 4x the fill rate for no visible gain with 720p/1080p skin assets.
 */
public final class RenderScaler {

	private static FrameBuffer target;

	private final boolean enabled;
	private SpriteBatch blitBatch;

	RenderScaler(boolean hiDpiRendering) {
		this.enabled = !hiDpiRendering;
	}

	/** Width of the framebuffer currently being rendered to; use instead of the backbuffer size inside render(). */
	public static int getTargetWidth() {
		return target != null ? target.getWidth() : Gdx.graphics.getBackBufferWidth();
	}

	public static int getTargetHeight() {
		return target != null ? target.getHeight() : Gdx.graphics.getBackBufferHeight();
	}

	void begin() {
		final int width = Gdx.graphics.getWidth();
		final int height = Gdx.graphics.getHeight();
		if (!enabled || width <= 0 || height <= 0 || Gdx.graphics.getBackBufferWidth() <= width) {
			disposeTarget();
			return;
		}
		if (target == null || target.getWidth() != width || target.getHeight() != height) {
			disposeTarget();
			target = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
			// Exact 2x upscale stays crisp with nearest; fractional scales need linear.
			final Texture.TextureFilter filter = Gdx.graphics.getBackBufferWidth() % width == 0
					? Texture.TextureFilter.Nearest : Texture.TextureFilter.Linear;
			target.getColorBufferTexture().setFilter(filter, filter);
		}
		target.begin();
		// Viewport and scissor calls from skins and scene2d are in points, which now equal target pixels.
		HdpiUtils.setMode(HdpiMode.Pixels);
	}

	void end() {
		if (target == null) {
			return;
		}
		HdpiUtils.setMode(HdpiMode.Logical);
		target.end();
		if (blitBatch == null) {
			blitBatch = new SpriteBatch(1);
		}
		final int width = target.getWidth();
		final int height = target.getHeight();
		blitBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
		blitBatch.disableBlending();
		blitBatch.begin();
		// FrameBuffer textures are bottom-up; draw with negative height to flip.
		blitBatch.draw(target.getColorBufferTexture(), 0, height, width, -height);
		blitBatch.end();
	}

	/** Restores the render target after a nested FrameBuffer, which always unbinds to the backbuffer. */
	public static void restoreTarget() {
		if (target != null) {
			target.bind();
			Gdx.gl.glViewport(0, 0, target.getWidth(), target.getHeight());
		} else {
			Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
			Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		}
	}

	private static void disposeTarget() {
		if (target != null) {
			target.dispose();
			target = null;
		}
	}

	void dispose() {
		disposeTarget();
		if (blitBatch != null) {
			blitBatch.dispose();
			blitBatch = null;
		}
	}
}
