package bms.player.beatoraja.launcher;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import java.util.Map;

/** Translates displayed enum names without changing persisted option values. */
final class KoreanLabels {
    private static final Map<String,String> NAMES = Map.ofEntries(
        Map.entry("PLAY_7KEYS", "7키 플레이"), Map.entry("PLAY_5KEYS", "5키 플레이"),
        Map.entry("PLAY_14KEYS", "14키 플레이"), Map.entry("PLAY_10KEYS", "10키 플레이"), Map.entry("PLAY_9KEYS", "9키 플레이"),
        Map.entry("PLAY_24KEYS", "24키 플레이"), Map.entry("PLAY_24KEYS_DOUBLE", "24키 더블"), Map.entry("PLAY_24KEYS_BATTLE", "24키 배틀"),
        Map.entry("PLAY_7KEYS_BATTLE", "7키 배틀"), Map.entry("PLAY_5KEYS_BATTLE", "5키 배틀"), Map.entry("PLAY_9KEYS_BATTLE", "9키 배틀"),
        Map.entry("MUSIC_SELECT", "곡 선택"), Map.entry("DECIDE", "플레이 준비"), Map.entry("RESULT", "결과"),
        Map.entry("KEY_CONFIG", "키 설정"), Map.entry("SKIN_SELECT", "스킨 선택"), Map.entry("SOUND_SET", "사운드"),
        Map.entry("THEME", "테마"), Map.entry("COURSE_RESULT", "코스 결과"),
        Map.entry("FULLSCREEN", "전체 화면"), Map.entry("WINDOW", "창 모드"), Map.entry("BORDERLESS", "테두리 없는 창"),
        Map.entry("NONE", "사용 안 함"), Map.entry("ON", "사용"), Map.entry("OFF", "사용 안 함"),
        Map.entry("ONCE", "한 번 재생"), Map.entry("LOOP", "반복 재생"),
        Map.entry("UNPROCESSED", "변경 없음"), Map.entry("FREQUENCY", "주파수 변경"), Map.entry("SPEED", "속도 변경"),
        Map.entry("AudioDevice", "오디오 장치"), Map.entry("TEMPORARY", "임시 폴더"),
        Map.entry("NO_TEMPORARY_FILES", "임시 파일 없이 읽기"), Map.entry("SONG_DIRECTORY", "곡 폴더"));
    static void install(Node node) {
        if (node instanceof ComboBox<?> combo && combo.getItems().stream().anyMatch(x -> x instanceof Enum<?>)) localize(combo);
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) install(child);
        if (node instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() != null) install(scroll.getContent());
    }
    private static <T> void localize(ComboBox<T> combo) {
        combo.setCellFactory(view -> cell()); combo.setButtonCell(cell());
    }
    private static <T> ListCell<T> cell() {
        return new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                String key = item instanceof Enum<?> e ? e.name() : String.valueOf(item);
                setText(empty || item == null ? "" : NAMES.getOrDefault(key, item.toString()));
            }
        };
    }
}
