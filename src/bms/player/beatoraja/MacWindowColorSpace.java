package bms.player.beatoraja;

import java.util.logging.Logger;

import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.macosx.ObjCRuntime;

/**
 * Tags the game window as sRGB. GLFW leaves an OpenGL window without a color space, so macOS passes its pixels to
 * the display unconverted; on wide-gamut (P3/XDR) panels that untagged content is handled differently from the
 * color-managed windows around it, which shows as oversaturation and brightness shifts. With an sRGB window the
 * system converts every frame to the display profile, as it does for other apps (same fix as SDL's macOS backend).
 */
final class MacWindowColorSpace {

	private MacWindowColorSpace() {
	}

	/** Must run on the main (AppKit) thread, i.e. the render thread with -XstartOnFirstThread. */
	static void applySrgb(long glfwWindow) {
		if (Platform.get() != Platform.MACOSX || glfwWindow == 0) {
			return;
		}
		try {
			final long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
			final long nsWindow = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
			if (send == 0 || nsWindow == 0) {
				return;
			}
			final long srgb = JNI.invokePPP(ObjCRuntime.objc_getClass("NSColorSpace"),
					ObjCRuntime.sel_getUid("sRGBColorSpace"), send);
			if (srgb == 0) {
				return;
			}
			JNI.invokePPPV(nsWindow, ObjCRuntime.sel_getUid("setColorSpace:"), srgb, send);
			final long applied = JNI.invokePPP(nsWindow, ObjCRuntime.sel_getUid("colorSpace"), send);
			final long name = applied != 0 ? JNI.invokePPP(applied, ObjCRuntime.sel_getUid("localizedName"), send) : 0;
			final long utf8 = name != 0 ? JNI.invokePPP(name, ObjCRuntime.sel_getUid("UTF8String"), send) : 0;
			Logger.getGlobal().info("ウィンドウの色空間 : " + (utf8 != 0 ? MemoryUtil.memUTF8(utf8) : "?"));
		} catch (Throwable e) {
			Logger.getGlobal().warning("ウィンドウの色空間を設定できません : " + e.getMessage());
		}
	}
}
