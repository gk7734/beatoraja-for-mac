import java.lang.instrument.Instrumentation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.badlogic.gdx.Gdx;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState.MainStateType;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.MusicSelectCommand;
import bms.player.beatoraja.skin.property.EventFactory;

/** Regression: key 9 used to start AWT even with no song selected and seize the Cocoa loop. */
public class DesktopEventSmokeAgent {
    private static MainController controller() {
        Object listener = Gdx.app.getApplicationListener();
        try {
            for (java.lang.reflect.Field field : listener.getClass().getDeclaredFields()) {
                if (field.getType() == MainController.class) {
                    field.setAccessible(true);
                    return (MainController) field.get(listener);
                }
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        throw new AssertionError("MainController missing from listener");
    }
    public static void premain(String args, Instrumentation instrumentation) {
        Thread thread = new Thread(() -> {
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (Gdx.graphics == null || Gdx.graphics.getFrameId() < 120) {
                    if (System.nanoTime() > deadline) throw new AssertionError("startup timeout");
                    Thread.sleep(50);
                }
                CountDownLatch events = new CountDownLatch(1);
                Gdx.app.postRunnable(() -> {
                    MainController main = controller();
                    MusicSelector select = (MusicSelector) main.getCurrentState();
                    EventFactory.getEvent("open_document").exec(select);
                    select.execute(MusicSelectCommand.SHOW_SONGS_ON_SAME_FOLDER);
                    EventFactory.getEvent("rival").exec(select);
                    events.countDown();
                });
                if (!events.await(5, TimeUnit.SECONDS)) throw new AssertionError("9/8/7 event timeout");
                long frame = Gdx.graphics.getFrameId();
                Thread.sleep(1000);
                if (Gdx.graphics.getFrameId() <= frame) throw new AssertionError("Cocoa loop seized");
                for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                    if (type.getName().equals("sun.lwawt.macosx.LWCToolkit"))
                        throw new AssertionError("AWT Cocoa toolkit initialized");
                }
                CountDownLatch skin = new CountDownLatch(1);
                Gdx.app.postRunnable(() -> {
                    MainController main = controller();
                    main.changeState(MainStateType.SKINCONFIG);
                    skin.countDown();
                });
                if (!skin.await(10, TimeUnit.SECONDS)) throw new AssertionError("skin settings timeout");
                frame = Gdx.graphics.getFrameId(); Thread.sleep(1000);
                if (Gdx.graphics.getFrameId() <= frame) throw new AssertionError("skin rendering stopped");
                System.out.println("PASS: 9/8/7 actions, no AWT toolkit, skin settings render");
                Gdx.app.postRunnable(() -> Gdx.app.exit());
            } catch (Throwable failure) {
                failure.printStackTrace(); Runtime.getRuntime().halt(2);
            }
        }, "Desktop regression watchdog");
        thread.setDaemon(true); thread.start();
    }
}
