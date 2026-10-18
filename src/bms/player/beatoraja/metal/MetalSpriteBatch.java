package bms.player.beatoraja.metal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;

/**
 * SpriteBatch whose flush hands the collected quads to Metal instead of a GL mesh. Every other SpriteBatch method
 * (vertex building, colour, blend state, shader switching) is libGDX's own.
 */
final class MetalSpriteBatch extends SpriteBatch {

	// SpriteBatch keeps its vertex array, fill index and current texture package-private.
	private static final VarHandle VERTICES, IDX, LAST_TEXTURE;

	static {
		try {
			final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(SpriteBatch.class, MethodHandles.lookup());
			VERTICES = lookup.findVarHandle(SpriteBatch.class, "vertices", float[].class);
			IDX = lookup.findVarHandle(SpriteBatch.class, "idx", int.class);
			LAST_TEXTURE = lookup.findVarHandle(SpriteBatch.class, "lastTexture", Texture.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private final float[] vertices = (float[]) VERTICES.get(this);
	private final Matrix4 combined = new Matrix4();

	@Override
	public void flush() {
		final int idx = (int) IDX.get(this);
		if (idx == 0) {
			return;
		}
		renderCalls++;
		totalRenderCalls++;
		final int sprites = idx / 20;
		if (sprites > maxSpritesInBatch) {
			maxSpritesInBatch = sprites;
		}
		final Texture texture = (Texture) LAST_TEXTURE.get(this);
		if (texture != null) {
			combined.set(getProjectionMatrix()).mul(getTransformMatrix());
			MetalRenderer.draw(vertices, idx, texture, combined.val, getShader(), isBlendingEnabled(), getBlendSrcFunc(),
					getBlendDstFunc(), getBlendSrcFuncAlpha(), getBlendDstFuncAlpha());
		}
		IDX.set(this, 0);
	}
}
