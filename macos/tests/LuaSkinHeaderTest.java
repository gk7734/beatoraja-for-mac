import java.nio.file.*;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.lua.LuaSkinLoader;
import bms.player.beatoraja.skin.lua.SkinLuaAccessor;

public class LuaSkinHeaderTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        int count = 0;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".luaskin")).toList()) {
                SkinHeader header = LuaSkinLoader.sandboxed(path).loadHeader(path);
                if (header == null) throw new AssertionError("Header failed: " + path);
                System.out.println(header.getSkinType() + " : " + header.getName());
                count++;
            }
        }
        if (count == 0) throw new AssertionError("No skin entry files");
        SkinLuaAccessor lua = new SkinLuaAccessor(false, root);
        lua.exec("local j=require('luajava'); assert(j.bindClass('com.badlogic.gdx.Input').Keys.F12); "
                + "assert(j.new==nil and j.newInstance==nil); "
                + "assert(not pcall(j.bindClass,'java.lang.Runtime')); "
                + "assert(not pcall(j.bindClass,'java.io.File')); "
                + "assert(type(os.date)=='function' and os.execute==nil and os.remove==nil and os.getenv==nil and package.loadlib==nil)");
        System.out.println("PASS: " + count + " headers; sandbox restrictions retained");
    }
}
