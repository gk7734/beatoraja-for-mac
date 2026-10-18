package bms.player.beatoraja.input;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import bms.player.beatoraja.PlayModeConfig.KeyboardConfig;
import bms.player.beatoraja.Resolution;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.Input.Keys;

/**
 * キーボード入力処理用クラス
 * 
 * @author exch
 */
public class KeyBoardInputProcesseor extends BMSPlayerInputDevice implements InputProcessor {

	public static final int MASK_SHIFT = 1 << 0;
	public static final int MASK_CTRL = 1 << 1;
	public static final int MASK_ALT = 1 << 2;

	private int[] keys = new int[] { Keys.Z, Keys.S, Keys.X, Keys.D, Keys.C, Keys.F, Keys.V, Keys.SHIFT_LEFT,
			Keys.CONTROL_LEFT, Keys.COMMA, Keys.L, Keys.PERIOD, Keys.SEMICOLON, Keys.SLASH, Keys.APOSTROPHE,
			Keys.BACKSLASH, Keys.SHIFT_RIGHT, Keys.CONTROL_RIGHT };
	private int[] control = new int[] { Keys.Q, Keys.W };

	private MouseScratchInput mouseScratchInput;

	private final IntArray reserved;
	/**
	 * 最後に押されたキー
	 */
	private int lastPressedKey = -1;

	private boolean textmode = false;

	/**
	 * 画面の解像度。マウスの入力イベント処理で使用
	 */
	private Resolution resolution;

	/**
	 * 各キーのon/off状態
	 */
	private final boolean[] keystate = new boolean[256];
	/**
	 * 各キーの状態変化時間
	 */
	private final long[] keytime = new long[256];
	/**
	 * 各キーが最後に押されたときで押されてる修飾キー
	 */
	private final int[] keymodifiers = new int[256];
	/**
	 * キーの最少入力間隔(ms)
	 */
	private int duration;
    private KeyboardLaneBindings laneBindings;
	private int[] secondaryKeys = new int[0];
	/**
	 * Direct HID keyboard input on macOS, or null to read keys from GLFW only.
	 */
	private MacHidKeyboard hid;
	/**
	 * Keys for which HID and GLFW were seen pressed together. Only these use HID input, so a key
	 * macOS maps differently on this keyboard layout can never trigger the wrong lane.
	 */
	private final boolean[] hidVerified = new boolean[256];
	/**
	 * HID key state as of the event currently being replayed, so a lane combining two keys sees
	 * the other key as it was at that moment rather than its latest state.
	 */
	private final boolean[] hidReplayState = new boolean[256];
	/**
	 * GLFW key events in arrival order. GLFW's key state only changes once per frame, so releasing and pressing
	 * a key again within one frame (back-to-back scratch long notes) left the state unchanged and the second
	 * press was lost. The events keep both edges.
	 */
	private final ArrayBlockingQueue<MacHidKeyboard.Event> glfwEvents = new ArrayBlockingQueue<>(1024);
	/** GLFW key state as of the event currently being replayed (see hidReplayState) */
	private final boolean[] glfwReplayState = new boolean[256];
	private final boolean[] glfwReplayKnown = new boolean[256];
	/** KeyboardConfig.alternateScratch */
	private boolean alternateScratch;
	/** Key indices of each turntable's two directions while playing (LaneProperty.getScratchKeyAssign), else empty */
	private int[][] scratchPairs = new int[0][];
	/** Per turntable: which of its two indices the next press goes to */
	private int[] nextScratch = new int[0];
	/** Per key index: the index its current press was sent to, -1 when up */
	private final int[] sentTo = new int[256];

	public KeyBoardInputProcesseor(BMSPlayerInputProcessor bmsPlayerInputProcessor, KeyboardConfig config, Resolution resolution) {
		super(bmsPlayerInputProcessor, Type.KEYBOARD);
		this.mouseScratchInput = new MouseScratchInput(bmsPlayerInputProcessor, this, config);
		this.setConfig(config);
		this.resolution = resolution;
		
		reserved = new IntArray();
		Arrays.stream(ControlKeys.values()).forEach(keys -> reserved.add(keys.keycode));
		
		Arrays.fill(keytime, Long.MIN_VALUE);
	}

	public void setConfig(KeyboardConfig config) {
		this.keys = config.getKeyAssign().clone();
        this.laneBindings = new KeyboardLaneBindings(keys, config.getSecondaryKeyAssign());
		this.secondaryKeys = KeyboardLaneBindings.normalize(config.getSecondaryKeyAssign(), keys.length);
		this.duration = config.getDuration();
		this.alternateScratch = config.isAlternateScratch();
		setScratchPairs(new int[0][]);
		this.control = new int[] { config.getStart(), config.getSelect() };
		mouseScratchInput.setConfig(config);
	}

	public boolean keyDown(int keycode) {
		setLastPressedKey(keycode);
		queueGlfwEvent(keycode, true);
		return true;
	}

	public boolean keyTyped(char keycode) {
		return false;
	}

	public boolean keyUp(int keycode) {
		queueGlfwEvent(keycode, false);
		return true;
	}

	/** Render thread (libGDX event processing); poll() consumes the queue on the input thread. */
	private void queueGlfwEvent(int keycode, boolean pressed) {
		if (keycode >= 0 && keycode < 256) {
			glfwEvents.offer(new MacHidKeyboard.Event(keycode, pressed, Gdx.input.getCurrentEventTime()));
		}
	}

	public void clear() {
		// Arrays.fill(keystate, false);
		Arrays.fill(keytime, Long.MIN_VALUE);
		lastPressedKey = -1;
        laneBindings.clear();
		Arrays.fill(sentTo, -1);
		Arrays.fill(nextScratch, 0);
		mouseScratchInput.clear();
	}

	/**
	 * Turntables whose presses alternate between their two directions (only while playing; menus use the
	 * directions to move). Cleared by setConfig.
	 */
	void setScratchPairs(int[][] pairs) {
		scratchPairs = pairs;
		nextScratch = new int[pairs.length];
		Arrays.fill(sentTo, -1);
	}

	void setHidKeyboard(MacHidKeyboard hid) {
		this.hid = hid;
	}

	public void poll(final long microtime) {
		final MacHidKeyboard hid = this.hid;
		final long starttime = bmsPlayerInputProcessor.getStartTime();
		if (hid != null) {
			// Apply HID key changes in order with their own timestamps, so taps shorter than the poll interval are kept.
			for (MacHidKeyboard.Event event; (event = hid.poll()) != null;) {
				if (textmode || !hidVerified[event.keycode()]) {
					continue;
				}
				final long time = Math.min(microtime, event.nanos() / 1000 - starttime);
				hidReplayState[event.keycode()] = event.pressed();
				for (int i = 0; i < keys.length; i++) {
					if (keys[i] == event.keycode() || secondaryKeys[i] == event.keycode()) {
						updateLane(i, time, this::isReplayKeyDown);
					}
				}
			}
		}
		// Then GLFW key events for keys HID does not report. Both edges of a release and re-press within one frame
		// arrive with the same time; the duration filter holds the press until the release has been judged.
		for (MacHidKeyboard.Event event; (event = glfwEvents.poll()) != null;) {
			final int key = event.keycode();
			if (textmode || (hid != null && hidVerified[key])) {
				continue;
			}
			glfwReplayState[key] = event.pressed();
			glfwReplayKnown[key] = true;
			final long time = Math.min(microtime, event.nanos() / 1000 - starttime);
			for (int i = 0; i < keys.length; i++) {
				if (keys[i] == key || secondaryKeys[i] == key) {
					updateLane(i, time, this::isReplayKeyDown);
				}
			}
		}
		if (!textmode) {
			for (int i = 0; i < keys.length; i++) {
				if (hid != null) {
					verifyHidKey(hid, keys[i]);
					verifyHidKey(hid, secondaryKeys[i]);
				}
				// For HID keys this also catches changes the duration filter dropped above, and focus loss.
				updateLane(i, microtime, this::isKeyDownNow);
			}

			final boolean startpressed = Gdx.input.isKeyPressed(control[0]);
			if (startpressed != keystate[control[0]]) {
				keystate[control[0]] = startpressed;
				this.bmsPlayerInputProcessor.startChanged(startpressed);
			}
			final boolean selectpressed = Gdx.input.isKeyPressed(control[1]);
			if (selectpressed != keystate[control[1]]) {
				keystate[control[1]] = selectpressed;
				this.bmsPlayerInputProcessor.setSelectPressed(selectpressed);
			}
		}
		
		for (ControlKeys key : ControlKeys.values()) {
			final boolean pressed = Gdx.input.isKeyPressed(key.keycode);
			if (!(textmode && key.text) && pressed != keystate[key.keycode]) {
				keystate[key.keycode] = pressed;
				keytime[key.keycode] = microtime;
				keymodifiers[key.keycode] = pressed ? currentlyHeldModifiers() : 0;
			}
		}
		
		mouseScratchInput.poll(microtime);
	}

	/**
	 * The judge reads only each lane's latest state, so a lane's next change waits at least this long even with an
	 * input duration of 0; otherwise a release and re-press applied in one poll would hide the release.
	 */
	private static final long MIN_CHANGE_INTERVAL = 2000;

	private void updateLane(int i, long time, java.util.function.IntPredicate pressed) {
		if (laneBindings.update(i, time, Math.max(duration * 1000L, MIN_CHANGE_INTERVAL), pressed)) {
			final boolean down = laneBindings.isDown(i);
			final int index = scratchTarget(i, down);
			this.bmsPlayerInputProcessor.keyChanged(this, time, index, down);
			this.bmsPlayerInputProcessor.setAnalogState(index, false, 0);
		}
	}

	/** The key index a change of lane i is reported as: alternating turntable directions, else i itself. */
	private int scratchTarget(int i, boolean down) {
		if (!alternateScratch || i >= sentTo.length) {
			return i;
		}
		for (int p = 0; p < scratchPairs.length; p++) {
			final int[] pair = scratchPairs[p];
			if (pair.length == 2 && (pair[0] == i || pair[1] == i)) {
				if (down) {
					sentTo[i] = pair[nextScratch[p]];
					nextScratch[p] ^= 1;
					return sentTo[i];
				}
				final int index = sentTo[i] >= 0 ? sentTo[i] : i;
				sentTo[i] = -1;
				return index;
			}
		}
		return i;
	}

	private void verifyHidKey(MacHidKeyboard hid, int key) {
		if (key >= 0 && !hidVerified[key] && Gdx.input.isKeyPressed(key) && hid.isPressed(key)) {
			hidVerified[key] = true;
			hidReplayState[key] = true;
		}
	}

	private boolean isKeyDownNow(int key) {
		return hid != null && hidVerified[key] ? hid.isPressed(key) : Gdx.input.isKeyPressed(key);
	}

	private boolean isReplayKeyDown(int key) {
		if (hidVerified[key]) {
			return hidReplayState[key] && MacHidKeyboard.isFocused();
		}
		return glfwReplayKnown[key] ? glfwReplayState[key] : Gdx.input.isKeyPressed(key);
	}

	private int currentlyHeldModifiers() {
		boolean shift = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT);
		boolean ctrl = Gdx.input.isKeyPressed(Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Keys.CONTROL_RIGHT);
		boolean alt = Gdx.input.isKeyPressed(Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Keys.ALT_RIGHT);
		return (shift ? MASK_SHIFT : 0) | (ctrl ? MASK_CTRL : 0) | (alt ? MASK_ALT : 0);
	}


	public boolean getKeyState(int keycode) {
		return keystate[keycode];
	}
	
	protected void setKeyState(int keycode, boolean pressed) {
		keystate[keycode] = pressed;
	}

	public boolean isKeyPressed(int keycode) {
		if(keystate[keycode] && keytime[keycode] != Long.MIN_VALUE) {
			keytime[keycode] = Long.MIN_VALUE;
			return true;
		}
		return false;
	}

	public boolean isKeyPressed(int keycode, int heldModifiers, int... notHeldModifiers) {
		if(keystate[keycode] && keytime[keycode] != Long.MIN_VALUE) {
			int modifiers = keymodifiers[keycode];
			if ((modifiers & heldModifiers) != heldModifiers) return false;
			for (int i = 0; i < notHeldModifiers.length; i++) {
				if ((modifiers & notHeldModifiers[i]) == notHeldModifiers[i]) return false;
			}
			keytime[keycode] = Long.MIN_VALUE;
			return true;
		}
		return false;
	}

	public boolean mouseMoved(int x, int y) {
		this.bmsPlayerInputProcessor.setMouseMoved(true);
		this.bmsPlayerInputProcessor.mousex = x * resolution.width / Gdx.graphics.getWidth();
		this.bmsPlayerInputProcessor.mousey = resolution.height - y * resolution.height / Gdx.graphics.getHeight();
		return false;
	}

	/**
	 * 旧InputProcessorのメソッド
	 * libGDX更新時に削除
	 */
	public boolean scrolled(int amount) {
		return scrolled(0, amount);
	}

	public boolean scrolled(float amountX, float amountY) {
		this.bmsPlayerInputProcessor.scrollX += amountX;
		this.bmsPlayerInputProcessor.scrollY += amountY;
		return false;
	}

	public boolean touchDown(int x, int y, int point, int button) {
		this.bmsPlayerInputProcessor.mousebutton = button;
		this.bmsPlayerInputProcessor.mousex = x * resolution.width / Gdx.graphics.getWidth();
		this.bmsPlayerInputProcessor.mousey = resolution.height - y * resolution.height
				/ Gdx.graphics.getHeight();
		this.bmsPlayerInputProcessor.mousepressed = true;
		return false;
	}

	public boolean touchDragged(int x, int y, int point) {
		this.bmsPlayerInputProcessor.mousex = x * resolution.width / Gdx.graphics.getWidth();
		this.bmsPlayerInputProcessor.mousey = resolution.height - y * resolution.height
				/ Gdx.graphics.getHeight();
		this.bmsPlayerInputProcessor.mousedragged = true;
		return false;
	}

    public boolean touchCancelled(int x, int y, int pointer, int button) { return touchUp(x, y, pointer, button); }

	public boolean touchUp(int arg0, int arg1, int arg2, int arg3) {
		return false;
	}

	public int getLastPressedKey() {
		return lastPressedKey;
	}

	public void setLastPressedKey(int lastPressedKey) {
		this.lastPressedKey = lastPressedKey;
	}

	public MouseScratchInput getMouseScratchInput() {
		return mouseScratchInput;
	}

	public void setTextInputMode(boolean textmode) {
		this.textmode = textmode;
	}
	
	public boolean isReservedKey(int key) {
		return reserved.contains(key);
	}
	
	public enum ControlKeys {
		NUM0(0, Keys.NUM_0, true),
		NUM1(1, Keys.NUM_1, true),
		NUM2(2, Keys.NUM_2, true),
		NUM3(3, Keys.NUM_3, true),
		NUM4(4, Keys.NUM_4, true),
		NUM5(5, Keys.NUM_5, true),
		NUM6(6, Keys.NUM_6, true),
		NUM7(7, Keys.NUM_7, true),
		NUM8(8, Keys.NUM_8, true),
		NUM9(9, Keys.NUM_9, true),
		
		F1(10, Keys.F1, false),
		F2(11, Keys.F2, false),
		F3(12, Keys.F3, false),
		F4(13, Keys.F4, false),
		F5(14, Keys.F5, false),
		F6(15, Keys.F6, false),
		F7(16, Keys.F7, false),
		F8(17, Keys.F8, false),
		F9(18, Keys.F9, false),
		F10(19, Keys.F10, false),
		F11(20, Keys.F11, false),
		F12(21, Keys.F12, false),
		
		UP(22, Keys.UP, false),
		DOWN(23, Keys.DOWN, false),
		LEFT(24, Keys.LEFT, false),
		RIGHT(25, Keys.RIGHT, false),
		
		ENTER(26, Keys.ENTER, false),
		DEL(27, Keys.FORWARD_DEL, false),
		ESCAPE(28, Keys.ESCAPE, false),
		;
		
		public final int id;
		
		public final int keycode;
		
		public final boolean text;
		
		private ControlKeys(int id, int keycode, boolean text) {
			this.id = id;
			this.keycode = keycode;
			this.text = text;
		}
	}

}