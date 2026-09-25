package bms.player.beatoraja;

import java.nio.file.*;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import bms.player.beatoraja.song.*;

/** Headless services used by the native Swift launcher. No JavaFX application is started. */
public final class MacMaintenance {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--defaults")) {
            Path destination = Path.of(args[1]);
            Files.createDirectories(destination);
            Json json = new Json();
            json.setUsePrototypes(false);
            json.setOutputType(OutputType.json);
            Config system = new Config();
            system.validate();
            PlayerConfig player = new PlayerConfig();
            player.setId("player1");
            player.validate();
            Files.writeString(destination.resolve("config_sys.json"), json.prettyPrint(system));
            Files.writeString(destination.resolve("config_player.json"), json.prettyPrint(player));
            return;
        }
        Config config = Config.read();
        if (args.length != 1 || (!args[0].equals("--scan") && !args[0].equals("--rebuild")))
            throw new IllegalArgumentException("Unknown maintenance operation");
        Class.forName("org.sqlite.JDBC");
        SongDatabaseAccessor db = new SQLiteSongDatabaseAccessor(config.getSongpath(), config.getBmsroot(),
            config.getPlayerpath() + "/" + config.getPlayername() + "/songreview.db", SQLiteSongDatabaseAccessor.SongUpdaterType.BATCHED,
            config.isScanSongArchives(), config.getSongArchiveExtractMode());
        SongInformationAccessor info = config.isUseSongInfo() ? new SongInformationAccessor(config.getSonginfopath()) : null;
        db.updateSongDatas(null, config.getBmsroot(), args[0].equals("--rebuild"), info);
        System.out.println("곡 검색 완료");
    }
}
