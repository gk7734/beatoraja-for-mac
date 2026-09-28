package bms.player.beatoraja;

import java.util.concurrent.locks.LockSupport;

/**
 * Replaces libGDX's Lwjgl3 Sync, which yield-spins through the last 1-2ms of every frame.
 * Frames are scheduled on an absolute timeline, so park overshoot does not accumulate.
 */
final class FrameLimiter {
	private long next;
	private int fps;

	void sync(int fps) {
		if (fps <= 0) {
			this.fps = 0;
			return;
		}
		final long period = 1_000_000_000L / fps;
		long now = System.nanoTime();
		if (fps != this.fps || now - next > period) {
			this.fps = fps;
			// First frame, FPS change or a stalled frame: restart the timeline instead of bursting to catch up.
			next = now + period;
			return;
		}
		while (next - now > 0) {
			LockSupport.parkNanos(next - now);
			now = System.nanoTime();
		}
		next += period;
	}
}
