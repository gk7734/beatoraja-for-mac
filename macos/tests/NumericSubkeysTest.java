package bms.player.beatoraja.input;

import bms.player.beatoraja.PlayModeConfig.KeyboardConfig;
import bms.player.beatoraja.PlayModeConfig.ControllerConfig;
import com.badlogic.gdx.utils.Json;

public class NumericSubkeysTest {
    static void require(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        NumericControlKeys keys = new NumericControlKeys();
        keys.update(1, true, 0);
        require(keys.consume(1, 0));
        require(!keys.consume(1, 0));
        keys.update(1, true, 0); // Another physical source pressed while first held.
        require(keys.isDown(1) && !keys.consume(1, 0));
        keys.update(1, false, 0);
        require(!keys.isDown(1) && !keys.consume(1, 0));
        keys.update(1, true, 2);
        require(!keys.consume(1, 1));
        require(keys.consume(1, 2));
        keys.clear(); require(!keys.isDown(1));
        int[] bindings = NumericControlKeys.bindings(new int[]{-1, 29, 900, -10}, 255);
        require(bindings[1] == 29 && bindings[2] == -1 && bindings[3] == -1 && bindings[9] == -1);
        Json json = new Json();
        KeyboardConfig keyboard = json.fromJson(KeyboardConfig.class, "{}");
        require(NumericControlKeys.bindings(keyboard.getControlKeys(), 255)[1] == -1);
        keyboard.setControlKeys(bindings);
        require(json.fromJson(KeyboardConfig.class, json.toJson(keyboard)).getControlKeys()[1] == 29);
        ControllerConfig controller = json.fromJson(ControllerConfig.class, "{}");
        controller.setControlKeys(new int[]{-1, 7});
        require(json.fromJson(ControllerConfig.class, json.toJson(controller)).getControlKeys()[1] == 7);
        java.util.Set<Integer> held = new java.util.HashSet<>();
        com.badlogic.gdx.Gdx.input = (com.badlogic.gdx.Input) java.lang.reflect.Proxy.newProxyInstance(
                NumericSubkeysTest.class.getClassLoader(), new Class[]{com.badlogic.gdx.Input.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isKeyPressed")) return held.contains((Integer)arguments[0]);
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
        KeyBoardInputProcesseor input = new KeyBoardInputProcesseor(null, keyboard, bms.player.beatoraja.Resolution.values()[0]);
        held.add(29); require(input.numericControlDown(1));
        held.clear(); held.add(com.badlogic.gdx.Input.Keys.NUM_1); require(input.numericControlDown(1));
        input.setTextInputMode(true); require(!input.numericControlDown(1));
        input.setTextInputMode(false); held.clear(); require(!input.numericControlDown(1));
        com.badlogic.gdx.Gdx.input = null;
        System.out.println("PASS: edges, hold, release, modifiers, invalid bindings, old/new JSON");
    }
}
