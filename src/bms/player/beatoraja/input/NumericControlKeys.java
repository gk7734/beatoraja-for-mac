package bms.player.beatoraja.input;

import java.util.Arrays;

/** One logical edge per numeric function, shared by all physical input sources. */
public final class NumericControlKeys {
    private final boolean[] down = new boolean[10];
    private final boolean[] pending = new boolean[10];
    private final int[] modifiers = new int[10];
    public synchronized void update(int number, boolean pressed, int heldModifiers) {
        if (pressed != down[number]) {
            down[number] = pressed;
            pending[number] = pressed;
            modifiers[number] = pressed ? heldModifiers : 0;
        }
    }
    public synchronized boolean isDown(int number) { return down[number]; }
    public synchronized boolean consume(int number, int required, int... forbidden) {
        if (!down[number] || !pending[number] || (modifiers[number] & required) != required) return false;
        for (int mask : forbidden) if ((modifiers[number] & mask) == mask) return false;
        pending[number] = false;
        return true;
    }
    public synchronized void clear() { Arrays.fill(down, false); Arrays.fill(pending, false); }
    public static int[] bindings(int[] source, int maximum) {
        int[] result = new int[10]; Arrays.fill(result, -1);
        if (source != null) for (int i = 0; i < Math.min(10, source.length); i++)
            if (source[i] >= 0 && source[i] <= maximum) result[i] = source[i];
        return result;
    }
}
