package bms.player.beatoraja.launcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class MacGameProcess {
    static Process start() throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of("-XstartOnFirstThread", "-Xms256m", "-Xmx4g", "--enable-native-access=ALL-UNNAMED,javafx.graphics",
            "--add-modules=javafx.controls,javafx.fxml,javafx.swing", "-Djava.library.path=" + System.getProperty("java.library.path"),
            "-cp", System.getProperty("java.class.path"), "bms.player.beatoraja.MacBootstrap", "-s"));
        return new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of("game.log").toFile())).start();
    }
}
