package bms.player.beatoraja.metal;

import java.lang.foreign.MemorySegment;

/** The MTLTexture behind a GL texture name, with the sampler state GL would keep on the texture object. */
final class MetalTexture {
	MemorySegment handle;
	int kind;
	int width;
	int height;
	int minFilter = 0x2600; // GL_NEAREST
	int magFilter = 0x2600;
	int wrapS = 0x812F; // GL_CLAMP_TO_EDGE
	int wrapT = 0x812F;

	void replace(MemorySegment handle, int kind, int width, int height) {
		final MemorySegment old = this.handle;
		this.handle = handle;
		this.kind = kind;
		this.width = width;
		this.height = height;
		MetalRenderer.releaseTexture(old);
	}

	void release() {
		MetalRenderer.releaseTexture(handle);
		handle = null;
	}
}
