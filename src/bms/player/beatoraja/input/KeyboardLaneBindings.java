package bms.player.beatoraja.input;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** Combines a lane's primary/secondary keys before producing press/release edges. */
public final class KeyboardLaneBindings {
    private final int[] primary, secondary;
    private final boolean[] down;
    private final long[] changedAt;
    public KeyboardLaneBindings(int[] primary, int[] secondary) {
        this.primary = primary.clone();
        this.secondary = normalize(secondary, primary.length);
        down = new boolean[primary.length];
        changedAt = new long[primary.length];
        clear();
    }
    public static int[] normalize(int[] keys, int length) {
        int[] result = new int[length]; Arrays.fill(result, -1);
        if (keys != null) for (int i = 0; i < Math.min(length, keys.length); i++)
            if (keys[i] >= 0 && keys[i] < 256) result[i] = keys[i];
        return result;
    }
    public boolean update(int lane, long time, long debounce, IntPredicate pressed) {
        boolean next = (primary[lane] >= 0 && pressed.test(primary[lane]))
                || (secondary[lane] >= 0 && pressed.test(secondary[lane]));
        if (next != down[lane] && time >= changedAt[lane] + debounce) {
            down[lane] = next; changedAt[lane] = time; return true;
        }
        return false;
    }
    public boolean isDown(int lane) { return down[lane]; }
    public void clear() { Arrays.fill(down, false); Arrays.fill(changedAt, Long.MIN_VALUE); }
}
