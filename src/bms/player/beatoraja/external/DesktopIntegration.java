package bms.player.beatoraja.external;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** OS integration without starting AWT's competing Cocoa event loop on macOS. */
public final class DesktopIntegration {
    private DesktopIntegration() {}
    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
    public static boolean isSupported() {
        return isMac() || java.awt.Desktop.isDesktopSupported();
    }
    public static void open(File file) throws IOException {
        if (isMac()) {
            launch(file.getAbsoluteFile().toURI().toASCIIString());
        } else {
            java.awt.Desktop.getDesktop().open(file);
        }
    }
    public static void browse(URI uri) throws IOException {
        if (isMac()) {
            launch(uri.toASCIIString());
        } else {
            java.awt.Desktop.getDesktop().browse(uri);
        }
    }
    private static void launch(String target) throws IOException {
        // Pass one URI argument directly; never interpret paths/URLs as shell commands.
        new ProcessBuilder("/usr/bin/open", target)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
}
