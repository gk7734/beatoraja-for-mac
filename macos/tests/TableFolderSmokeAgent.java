import java.lang.instrument.Instrumentation;
import com.badlogic.gdx.Gdx;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.TableBar;

public class TableFolderSmokeAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        Thread watchdog = new Thread(() -> {
            try {
                long deadline = System.nanoTime() + 40_000_000_000L;
                while (Gdx.graphics == null || Gdx.graphics.getFrameId() < 120) {
                    if (System.nanoTime() > deadline) throw new AssertionError("Startup timeout");
                    Thread.sleep(50);
                }
                Gdx.app.postRunnable(() -> {
                    try {
                        Object listener = Gdx.app.getApplicationListener(); MainController main = null;
                        for (var field : listener.getClass().getDeclaredFields()) {
                            if (field.getType() == MainController.class) { field.setAccessible(true); main=(MainController)field.get(listener); }
                        }
                        var manager = ((MusicSelector)main.getCurrentState()).getBarManager();
                        var field = manager.getClass().getDeclaredField("tables"); field.setAccessible(true);
                        TableBar[] tables = (TableBar[])field.get(manager);
                        if (tables.length < Integer.parseInt(args)) throw new AssertionError("Missing table folders");
                        System.out.println("PASS: game constructed " + tables.length + " difficulty table folders");
                        Gdx.app.exit();
                    } catch (Throwable e) { e.printStackTrace(); Runtime.getRuntime().halt(2); }
                });
            } catch (Throwable e) { e.printStackTrace(); Runtime.getRuntime().halt(2); }
        });
        watchdog.setDaemon(true);watchdog.start();
    }
}
