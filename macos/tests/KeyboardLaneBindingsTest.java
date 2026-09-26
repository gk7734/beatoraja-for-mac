import java.util.HashSet;
import java.util.Set;
import bms.player.beatoraja.input.KeyboardLaneBindings;
import bms.player.beatoraja.PlayModeConfig.KeyboardConfig;
import com.badlogic.gdx.utils.Json;

public class KeyboardLaneBindingsTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        KeyboardLaneBindings lanes = new KeyboardLaneBindings(new int[]{29,-1,31},new int[]{30,32,31});
        Set<Integer> held = new HashSet<>();
        held.add(29);check(lanes.update(0,0,0,held::contains) && lanes.isDown(0));
        held.add(30);check(!lanes.update(0,1,0,held::contains));
        held.remove(29);check(!lanes.update(0,2,0,held::contains) && lanes.isDown(0));
        held.remove(30);check(lanes.update(0,3,0,held::contains) && !lanes.isDown(0));
        held.add(30);check(lanes.update(0,4,0,held::contains) && lanes.isDown(0));
        held.add(32);check(lanes.update(1,5,0,held::contains) && lanes.isDown(1));
        held.add(31);check(lanes.update(2,6,0,held::contains));
        check(!lanes.update(2,7,0,held::contains));
        held.remove(31);check(!lanes.update(2,8,10,held::contains));
        check(lanes.update(2,16,10,held::contains));
        lanes.clear();check(!lanes.isDown(0));
        Json json = new Json();
        KeyboardConfig old = json.fromJson(KeyboardConfig.class,"{}");
        for (int code: old.getSecondaryKeyAssign()) check(code == -1);
        old.setSecondaryKeyAssign(new int[]{29,999,-2});
        KeyboardConfig reloaded = json.fromJson(KeyboardConfig.class,json.toJson(old));
        check(reloaded.getSecondaryKeyAssign()[0]==29);
        check(reloaded.getSecondaryKeyAssign()[1]==-1 && reloaded.getSecondaryKeyAssign()[2]==-1);
        System.out.println("PASS: primary/secondary, overlapping holds, release, secondary-only, debounce, JSON compatibility");
    }
}
