package bms.player.beatoraja;

import java.util.logging.Logger;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import bms.player.beatoraja.Config.DisplayMode;

/**
 * Chooses the in-game resolution from the system display (Config.autoResolution).
 * Sizes are in points like the game window: fullscreen uses the display mode, a window the largest size that fits
 * the work area (menu bar and Dock excluded) below its title bar. Among the sizes that fit, fullscreen prefers the
 * display's aspect ratio (the 16:9 skin is centred with bars, Config.keepSkinAspect) and a window 16:9, the skin's
 * own ratio, so the window has no bars.
 */
final class AutoResolution {

	/** macOS window title bar (points) */
	private static final int TITLE_BAR = 28;
	/** aspect ratios closer than this count as the display's */
	private static final double ASPECT_TOLERANCE = 0.03;
	/** design height of typical skins (1920x1080): smaller resolutions render at the Retina density */
	private static final int SKIN_HEIGHT = 1080;

	private AutoResolution() {
	}

	/**
	 * @param resolution 選択した解像度
	 * @param hiDpi Retinaの画素密度で描画する(解像度が一般的なスキンの設計サイズより小さい場合)
	 */
	record Result(Resolution resolution, boolean hiDpi) {
	}

	static Result choose(DisplayMode mode) {
		// initializes GLFW (no-op when it already is)
		Lwjgl3ApplicationConfiguration.getDisplayMode();
		final long monitor = GLFW.glfwGetPrimaryMonitor();
		if (monitor == 0) {
			return null;
		}
		final GLFWVidMode video = GLFW.glfwGetVideoMode(monitor);
		if (video == null) {
			return null;
		}
		int width = video.width();
		int height = video.height();
		float contentScale = 1f;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			if (mode != DisplayMode.FULLSCREEN) {
				final var x = stack.mallocInt(1);
				final var y = stack.mallocInt(1);
				final var w = stack.mallocInt(1);
				final var h = stack.mallocInt(1);
				GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
				if (w.get(0) > 0 && h.get(0) > 0) {
					width = w.get(0);
					height = h.get(0) - (mode == DisplayMode.WINDOW ? TITLE_BAR : 0);
				}
			}
			final var sx = stack.mallocFloat(1);
			final var sy = stack.mallocFloat(1);
			GLFW.glfwGetMonitorContentScale(monitor, sx, sy);
			contentScale = sx.get(0);
		}
		final double aspect = mode == DisplayMode.WINDOW ? 16.0 / 9.0 : (double) video.width() / video.height();
		final Result result = pick(width, height, aspect, contentScale);
		Logger.getGlobal().info(String.format("自動解像度 : %s (%dx%d) display %dx%d area %dx%d scale %.1f%s",
				result.resolution().name(), result.resolution().width, result.resolution().height, video.width(),
				video.height(), width, height, contentScale, result.hiDpi() ? " HiDPI" : ""));
		return result;
	}

	/**
	 * @param width 使える幅(ポイント)
	 * @param height 使える高さ(ポイント)
	 * @param aspect 優先する縦横比
	 * @param contentScale ディスプレイの画素密度(Retinaは2)
	 */
	static Result pick(int width, int height, double aspect, float contentScale) {
		Resolution best = null;
		boolean bestSameAspect = false;
		for (Resolution r : Resolution.values()) {
			if (r.width > width || r.height > height) {
				continue;
			}
			final boolean sameAspect = Math.abs((double) r.width / r.height - aspect) <= ASPECT_TOLERANCE;
			if (best == null || (sameAspect && !bestSameAspect)
					|| (sameAspect == bestSameAspect && (long) r.width * r.height > (long) best.width * best.height)) {
				best = r;
				bestSameAspect = sameAspect;
			}
		}
		if (best == null) {
			best = Resolution.SD;
		}
		return new Result(best, contentScale > 1f && best.height < SKIN_HEIGHT);
	}
}
