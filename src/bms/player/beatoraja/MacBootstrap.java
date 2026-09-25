package bms.player.beatoraja;

/** Dedicated game process owned by the Swift launcher. */
public final class MacBootstrap {
    public static void main(String[] args) {
        java.util.Locale.setDefault(java.util.Locale.KOREAN);
        int status = 0;
        try {
            // LWJGL 3 returns only after the window loop and resource disposal finish.
            MainLoader.main(args);
        } catch (Throwable failure) {
            failure.printStackTrace();
            status = 1;
        }
        // Audio/MIDI/AWT worker threads can otherwise keep this child JVM alive.
        // Explicit process exit also runs the registered log-flushing shutdown hooks.
        System.exit(status);
    }
}
