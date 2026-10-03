package bms.player.beatoraja.input;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.logging.Logger;

import org.lwjgl.glfw.GLFW;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.DefaultLwjgl3Input;

/**
 * Reads the keyboard through IOHIDManager on its own thread.
 * GLFW only delivers key events when the render loop calls glfwPollEvents, so key timestamps
 * are otherwise quantized to the frame interval. HID callbacks arrive as soon as the key changes.
 * Requires the macOS Input Monitoring permission; without it the game keeps using GLFW.
 */
public final class MacHidKeyboard {

	private static final int KEYBOARD_PAGE = 0x07;
	private static final int REQUEST_LISTEN_EVENT = 1;
	private static final int ACCESS_GRANTED = 0;
	private static final int ACCESS_UNKNOWN = 2;

	/** A key change observed by the HID thread, timestamped with System.nanoTime(). */
	record Event(int keycode, boolean pressed, long nanos) {}

	private static volatile boolean focused = true;
	private static volatile MacHidKeyboard active;

	/** HID keyboard usage -> libGDX keycode, -1 when unmapped. */
	private final int[] usageToKey;
	private final boolean[] mapped = new boolean[256];
	private final AtomicIntegerArray pressed = new AtomicIntegerArray(256);
	/** Keys already held when the window gained focus; GLFW ignores them until released, so do we. */
	private final AtomicIntegerArray stale = new AtomicIntegerArray(256);
	private final ArrayBlockingQueue<Event> events = new ArrayBlockingQueue<>(1024);

	private MethodHandle valueGetElement;
	private MethodHandle elementGetUsagePage;
	private MethodHandle elementGetUsage;
	private MethodHandle valueGetIntegerValue;

	private MacHidKeyboard(int[] usageToKey) {
		this.usageToKey = usageToKey;
		for (int key : usageToKey) {
			if (key >= 0) {
				mapped[key] = true;
			}
		}
	}

	/** Called from the window listener. Unfocused windows must not receive keys, matching GLFW. */
	public static void setFocused(boolean focused) {
		final MacHidKeyboard keyboard = active;
		if (focused && !MacHidKeyboard.focused && keyboard != null) {
			for (int key = 0; key < 256; key++) {
				if (keyboard.pressed.get(key) != 0) {
					keyboard.stale.set(key, 1);
				}
			}
		}
		MacHidKeyboard.focused = focused;
	}

	static boolean isFocused() {
		return focused;
	}

	/** Starts the HID thread. Returns null when unavailable, in which case callers keep using GLFW. */
	static MacHidKeyboard start() {
		if (!System.getProperty("os.name", "").startsWith("Mac") || !(Gdx.input instanceof DefaultLwjgl3Input input)) {
			return null;
		}
		final MacHidKeyboard keyboard = new MacHidKeyboard(buildUsageMap(input));
		final CompletableFuture<Boolean> opened = new CompletableFuture<>();
		final Thread thread = new Thread(() -> keyboard.run(opened), "HID keyboard");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
		try {
			if (opened.get(3, TimeUnit.SECONDS)) {
				Logger.getGlobal().info("HID 키보드 직접 입력 사용");
				active = keyboard;
				return keyboard;
			}
		} catch (Exception e) {
			opened.complete(false);
			Logger.getGlobal().warning("HID 키보드 초기화 시간 초과 : " + e);
		}
		return null;
	}

	/** Whether this keyboard can report the given libGDX keycode. */
	boolean maps(int keycode) {
		return keycode >= 0 && keycode < 256 && mapped[keycode];
	}

	boolean isPressed(int keycode) {
		return focused && maps(keycode) && pressed.get(keycode) != 0 && stale.get(keycode) == 0;
	}

	Event poll() {
		return events.poll();
	}

	private void run(CompletableFuture<Boolean> opened) {
		try {
			final Linker linker = Linker.nativeLinker();
			final Arena arena = Arena.global();
			final SymbolLookup iokit = SymbolLookup.libraryLookup("/System/Library/Frameworks/IOKit.framework/IOKit", arena);
			final SymbolLookup cf = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena);

			final int access = (int) linker.downcallHandle(iokit.findOrThrow("IOHIDCheckAccess"), FunctionDescriptor.of(JAVA_INT, JAVA_INT))
					.invokeExact(REQUEST_LISTEN_EVENT);
			if (access != ACCESS_GRANTED) {
				boolean granted = false;
				if (access == ACCESS_UNKNOWN) {
					// Shows the system prompt once. Access applies from the next game launch.
					granted = (boolean) linker.downcallHandle(iokit.findOrThrow("IOHIDRequestAccess"), FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT))
							.invokeExact(REQUEST_LISTEN_EVENT);
				}
				if (!granted) {
					Logger.getGlobal().info("입력 모니터링 권한이 없어 기존 키보드 입력을 사용합니다");
					opened.complete(false);
					return;
				}
			}

			valueGetElement = linker.downcallHandle(iokit.findOrThrow("IOHIDValueGetElement"), FunctionDescriptor.of(ADDRESS, ADDRESS));
			elementGetUsagePage = linker.downcallHandle(iokit.findOrThrow("IOHIDElementGetUsagePage"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
			elementGetUsage = linker.downcallHandle(iokit.findOrThrow("IOHIDElementGetUsage"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
			valueGetIntegerValue = linker.downcallHandle(iokit.findOrThrow("IOHIDValueGetIntegerValue"), FunctionDescriptor.of(JAVA_LONG, ADDRESS));

			final MemorySegment manager = (MemorySegment) linker.downcallHandle(iokit.findOrThrow("IOHIDManagerCreate"),
					FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT)).invokeExact(MemorySegment.NULL, 0);

			// Match keyboards only: kIOHIDDeviceUsagePageKey = Generic Desktop, kIOHIDDeviceUsageKey = Keyboard.
			final MethodHandle createString = linker.downcallHandle(cf.findOrThrow("CFStringCreateWithCString"),
					FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
			final MethodHandle createNumber = linker.downcallHandle(cf.findOrThrow("CFNumberCreate"),
					FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
			final int utf8 = 0x08000100;
			final long sint32 = 3;
			final MemorySegment keys = arena.allocate(ADDRESS, 2);
			final MemorySegment values = arena.allocate(ADDRESS, 2);
			keys.setAtIndex(ADDRESS, 0, (MemorySegment) createString.invokeExact(MemorySegment.NULL, arena.allocateFrom("DeviceUsagePage"), utf8));
			keys.setAtIndex(ADDRESS, 1, (MemorySegment) createString.invokeExact(MemorySegment.NULL, arena.allocateFrom("DeviceUsage"), utf8));
			values.setAtIndex(ADDRESS, 0, (MemorySegment) createNumber.invokeExact(MemorySegment.NULL, sint32, arena.allocateFrom(JAVA_INT, 0x01)));
			values.setAtIndex(ADDRESS, 1, (MemorySegment) createNumber.invokeExact(MemorySegment.NULL, sint32, arena.allocateFrom(JAVA_INT, 0x06)));
			final MemorySegment matching = (MemorySegment) linker.downcallHandle(cf.findOrThrow("CFDictionaryCreate"),
					FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS))
					.invokeExact(MemorySegment.NULL, keys, values, 2L,
							cf.findOrThrow("kCFTypeDictionaryKeyCallBacks"), cf.findOrThrow("kCFTypeDictionaryValueCallBacks"));
			linker.downcallHandle(iokit.findOrThrow("IOHIDManagerSetDeviceMatching"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS))
					.invokeExact(manager, matching);

			final MethodHandle callback = MethodHandles.lookup().bind(this, "onValue",
					MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class, MemorySegment.class));
			final MemorySegment stub = linker.upcallStub(callback, FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, ADDRESS), arena);
			linker.downcallHandle(iokit.findOrThrow("IOHIDManagerRegisterInputValueCallback"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS))
					.invokeExact(manager, stub, MemorySegment.NULL);

			final MemorySegment runLoop = (MemorySegment) linker.downcallHandle(cf.findOrThrow("CFRunLoopGetCurrent"), FunctionDescriptor.of(ADDRESS))
					.invokeExact();
			final MemorySegment defaultMode = cf.findOrThrow("kCFRunLoopDefaultMode").reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
			linker.downcallHandle(iokit.findOrThrow("IOHIDManagerScheduleWithRunLoop"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS))
					.invokeExact(manager, runLoop, defaultMode);

			final int result = (int) linker.downcallHandle(iokit.findOrThrow("IOHIDManagerOpen"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
					.invokeExact(manager, 0);
			if (result != 0) {
				Logger.getGlobal().warning("IOHIDManagerOpen 실패 : 0x" + Integer.toHexString(result));
				opened.complete(false);
				return;
			}
			if (!opened.complete(true)) {
				// start() already gave up waiting; the game is using GLFW input.
				return;
			}
			linker.downcallHandle(cf.findOrThrow("CFRunLoopRun"), FunctionDescriptor.ofVoid()).invokeExact();
		} catch (Throwable e) {
			Logger.getGlobal().warning("HID 키보드 초기화 실패 : " + e);
			opened.complete(false);
		}
	}

	/** IOHIDValueCallback. Must never throw: an exception in an upcall aborts the JVM. */
	@SuppressWarnings("unused")
	private void onValue(MemorySegment context, int result, MemorySegment sender, MemorySegment value) {
		try {
			final long nanos = System.nanoTime();
			final MemorySegment element = (MemorySegment) valueGetElement.invokeExact(value);
			if ((int) elementGetUsagePage.invokeExact(element) != KEYBOARD_PAGE) {
				return;
			}
			final int usage = (int) elementGetUsage.invokeExact(element);
			if (usage < 0 || usage >= usageToKey.length || usageToKey[usage] < 0) {
				return;
			}
			final int keycode = usageToKey[usage];
			final boolean down = (long) valueGetIntegerValue.invokeExact(value) != 0;
			if (pressed.getAndSet(keycode, down ? 1 : 0) == (down ? 1 : 0)) {
				return;
			}
			if (!down && stale.getAndSet(keycode, 0) != 0) {
				return;
			}
			if (focused) {
				events.offer(new Event(keycode, down, nanos));
			}
		} catch (Throwable ignored) {
		}
	}

	/**
	 * Maps HID keyboard usages to GLFW keys, then through libGDX's own GLFW conversion so keycodes
	 * match the saved key config exactly. Keys macOS remaps per keyboard type (ISO section/grave,
	 * JIS extras, caps lock, PC-only keys) and Command, which libGDX folds into one SYM keycode for
	 * both sides, are left unmapped and keep using GLFW.
	 */
	private static int[] buildUsageMap(DefaultLwjgl3Input input) {
		final int[] glfw = new int[256];
		Arrays.fill(glfw, -1);
		for (int i = 0; i < 26; i++) {
			glfw[0x04 + i] = GLFW.GLFW_KEY_A + i;
		}
		for (int i = 0; i < 9; i++) {
			glfw[0x1E + i] = GLFW.GLFW_KEY_1 + i;
		}
		glfw[0x27] = GLFW.GLFW_KEY_0;
		glfw[0x28] = GLFW.GLFW_KEY_ENTER;
		glfw[0x29] = GLFW.GLFW_KEY_ESCAPE;
		glfw[0x2A] = GLFW.GLFW_KEY_BACKSPACE;
		glfw[0x2B] = GLFW.GLFW_KEY_TAB;
		glfw[0x2C] = GLFW.GLFW_KEY_SPACE;
		glfw[0x2D] = GLFW.GLFW_KEY_MINUS;
		glfw[0x2E] = GLFW.GLFW_KEY_EQUAL;
		glfw[0x2F] = GLFW.GLFW_KEY_LEFT_BRACKET;
		glfw[0x30] = GLFW.GLFW_KEY_RIGHT_BRACKET;
		glfw[0x31] = GLFW.GLFW_KEY_BACKSLASH;
		glfw[0x33] = GLFW.GLFW_KEY_SEMICOLON;
		glfw[0x34] = GLFW.GLFW_KEY_APOSTROPHE;
		glfw[0x36] = GLFW.GLFW_KEY_COMMA;
		glfw[0x37] = GLFW.GLFW_KEY_PERIOD;
		glfw[0x38] = GLFW.GLFW_KEY_SLASH;
		for (int i = 0; i < 12; i++) {
			glfw[0x3A + i] = GLFW.GLFW_KEY_F1 + i;
		}
		glfw[0x49] = GLFW.GLFW_KEY_INSERT;
		glfw[0x4A] = GLFW.GLFW_KEY_HOME;
		glfw[0x4B] = GLFW.GLFW_KEY_PAGE_UP;
		glfw[0x4C] = GLFW.GLFW_KEY_DELETE;
		glfw[0x4D] = GLFW.GLFW_KEY_END;
		glfw[0x4E] = GLFW.GLFW_KEY_PAGE_DOWN;
		glfw[0x4F] = GLFW.GLFW_KEY_RIGHT;
		glfw[0x50] = GLFW.GLFW_KEY_LEFT;
		glfw[0x51] = GLFW.GLFW_KEY_DOWN;
		glfw[0x52] = GLFW.GLFW_KEY_UP;
		glfw[0x54] = GLFW.GLFW_KEY_KP_DIVIDE;
		glfw[0x55] = GLFW.GLFW_KEY_KP_MULTIPLY;
		glfw[0x56] = GLFW.GLFW_KEY_KP_SUBTRACT;
		glfw[0x57] = GLFW.GLFW_KEY_KP_ADD;
		glfw[0x58] = GLFW.GLFW_KEY_KP_ENTER;
		for (int i = 0; i < 9; i++) {
			glfw[0x59 + i] = GLFW.GLFW_KEY_KP_1 + i;
		}
		glfw[0x62] = GLFW.GLFW_KEY_KP_0;
		glfw[0x63] = GLFW.GLFW_KEY_KP_DECIMAL;
		glfw[0x67] = GLFW.GLFW_KEY_KP_EQUAL;
		for (int i = 0; i < 12; i++) {
			glfw[0x68 + i] = GLFW.GLFW_KEY_F13 + i;
		}
		glfw[0xE0] = GLFW.GLFW_KEY_LEFT_CONTROL;
		glfw[0xE1] = GLFW.GLFW_KEY_LEFT_SHIFT;
		glfw[0xE2] = GLFW.GLFW_KEY_LEFT_ALT;
		glfw[0xE4] = GLFW.GLFW_KEY_RIGHT_CONTROL;
		glfw[0xE5] = GLFW.GLFW_KEY_RIGHT_SHIFT;
		glfw[0xE6] = GLFW.GLFW_KEY_RIGHT_ALT;

		final int[] map = new int[256];
		for (int usage = 0; usage < map.length; usage++) {
			final int key = glfw[usage] >= 0 ? input.getGdxKeyCode(glfw[usage]) : -1;
			map[usage] = key > 0 && key < 256 ? key : -1;
		}
		return map;
	}
}
