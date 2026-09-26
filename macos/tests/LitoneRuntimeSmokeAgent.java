import java.lang.instrument.Instrumentation;
import java.util.concurrent.*;
import com.badlogic.gdx.Gdx;
import bms.player.beatoraja.*;
import bms.player.beatoraja.skin.*;
import bms.player.beatoraja.decide.MusicDecide;
import bms.player.beatoraja.result.*;

/** Uses a separate data directory with a user-supplied LITONE12 installation. */
public class LitoneRuntimeSmokeAgent {
    private static MainController controller() throws Exception {
        Object listener = Gdx.app.getApplicationListener();
        for (var field : listener.getClass().getDeclaredFields()) {
            if (field.getType() == MainController.class) {
                field.setAccessible(true); return (MainController)field.get(listener);
            }
        }
        throw new AssertionError("Missing controller");
    }
    public static void premain(String args, Instrumentation instrumentation) {
        Thread watchdog = new Thread(() -> {
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
                while (Gdx.graphics == null || Gdx.graphics.getFrameId() < 120) {
                    if (System.nanoTime() > deadline) throw new AssertionError("Startup timeout");
                    Thread.sleep(50);
                }
                CompletableFuture<Void> loaded = new CompletableFuture<>();
                Gdx.app.postRunnable(() -> {
                    try {
                        MainController main = controller();
                        if (!main.getCurrentState().getSkin().header.getName().contains("LITONE12"))
                            throw new AssertionError("Select silently fell back to default skin");
                        System.out.println("PASS: LITONE12 MUSIC_SELECT rendered");
                        // Result skins expect the gauge history normally produced by gameplay.
                        com.badlogic.gdx.utils.FloatArray[] gauges = new com.badlogic.gdx.utils.FloatArray[10];
                        for (int i = 0; i < gauges.length; i++) {
                            gauges[i] = new com.badlogic.gdx.utils.FloatArray(); gauges[i].add(80);
                        }
                        main.getPlayerResource().setGauge(gauges);
                        main.getPlayerResource().setCourseBMSFiles(new java.nio.file.Path[0]);
                        MainState[] states = {new MusicDecide(main), new MusicResult(main), new CourseResult(main)};
                        SkinType[] types = {SkinType.DECIDE, SkinType.RESULT, SkinType.COURSE_RESULT};
                        String[] paths = {"Decide/decide", "Result/result", "Result/course"};
                        for (int i = 0; i < states.length; i++) {
                            SkinConfig config = new SkinConfig("skin/LITONE12/" + paths[i] + ".luaskin");
                            Skin skin = SkinLoader.load(states[i], types[i], config);
                            if (skin == null || !skin.header.getName().contains("LITONE12"))
                                throw new AssertionError("Skin load failed: " + types[i]);
                            skin.prepare(states[i]);
                            System.out.println("PASS: LITONE12 " + types[i] + " loaded/prepared");
                            skin.dispose();
                        }
                        loaded.complete(null);
                    } catch (Throwable e) { loaded.completeExceptionally(e); }
                });
                loaded.get(40, TimeUnit.SECONDS);
                Gdx.app.postRunnable(() -> Gdx.app.exit());
            } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(2); }
        }, "Litone regression watchdog");
        watchdog.setDaemon(true); watchdog.start();
    }
}
