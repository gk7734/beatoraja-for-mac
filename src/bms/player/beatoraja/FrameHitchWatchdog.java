package bms.player.beatoraja;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.logging.Logger;

/**
 * Logs render-loop stalls with the stack the render thread was stuck in.
 * JFR samples cannot tell one 100ms glfwSwapBuffers call from ten 10ms ones, so a stalled frame is
 * caught while it is still stalled: the render thread's stack is captured once it passes the threshold.
 */
final class FrameHitchWatchdog extends Thread {

	private static final long THRESHOLD_NANOS = 50_000_000L;
	private static final long POLL_MILLIS = 10;

	private final Thread renderThread;
	private volatile long frameStart;
	private volatile String state = "";

	FrameHitchWatchdog(Thread renderThread) {
		super("Frame hitch watchdog");
		this.renderThread = renderThread;
		setDaemon(true);
	}

	/** Called by the render thread at the start of every loop iteration. */
	void frameStarted(String currentState) {
		final long now = System.nanoTime();
		final long previous = frameStart;
		frameStart = now;
		if (previous != 0 && now - previous >= THRESHOLD_NANOS) {
			Logger.getGlobal().warning(String.format("[hitch] frame took %.0fms (%s)", (now - previous) / 1e6, state));
		}
		state = currentState;
	}

	@Override
	public void run() {
		long reportedFrame = 0;
		long gcCount = gcCount();
		while (true) {
			try {
				Thread.sleep(POLL_MILLIS);
			} catch (InterruptedException e) {
				return;
			}
			final long start = frameStart;
			if (start == 0 || start == reportedFrame || System.nanoTime() - start < THRESHOLD_NANOS) {
				gcCount = gcCount();
				continue;
			}
			final StackTraceElement[] stack = renderThread.getStackTrace();
			if (frameStart != start) {
				// The frame finished while the stack was being captured; the stack belongs to the next frame.
				continue;
			}
			reportedFrame = start;
			final long gcNow = gcCount();
			final StringBuilder sb = new StringBuilder("[hitch] render thread stalled ")
					.append((System.nanoTime() - start) / 1_000_000).append("ms in ").append(state)
					.append(gcNow != gcCount ? " (GC ran)" : "").append(", stack:");
			for (int i = 0; i < Math.min(stack.length, 14); i++) {
				sb.append("\n    at ").append(stack[i]);
			}
			Logger.getGlobal().warning(sb.toString());
			gcCount = gcNow;
		}
	}

	private static long gcCount() {
		long count = 0;
		final List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
		for (GarbageCollectorMXBean bean : beans) {
			count += Math.max(0, bean.getCollectionCount());
		}
		return count;
	}
}
