package bms.player.beatoraja.launcher;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Presentation shell; the existing FXML controllers still own all settings. */
public final class ModernLauncher {
    private ModernLauncher() {}

    public static void install(VBox root) {
        TabPane tabs = (TabPane) root.getChildren().stream().filter(n -> n instanceof TabPane).findFirst().orElseThrow();
        HBox profile = (HBox) root.lookup("#playerPanel");
        HBox actions = (HBox) root.lookup("#controlPanel");
        Hyperlink update = (Hyperlink) root.lookup("#newversion");
        root.getStylesheets().add(ModernLauncher.class.getResource("ModernLauncher.css").toExternalForm());
        root.getStyleClass().add("launcher-root");
        root.setPrefSize(1180, 800);
        root.getChildren().clear();

        Label logo = new Label("b."); logo.getStyleClass().add("brand-mark");
        Label brand = new Label("beatoraja"); brand.getStyleClass().add("brand-name");
        Label edition = new Label("설정"); edition.getStyleClass().add("eyebrow");
        VBox brandText = new VBox(3, brand, edition);
        HBox brandRow = new HBox(12, logo, brandText); brandRow.setAlignment(Pos.CENTER_LEFT);
        Label settingsLabel = new Label("설정"); settingsLabel.getStyleClass().add("nav-heading");
        VBox nav = new VBox(5); ToggleGroup group = new ToggleGroup();
        Label pageTitle = new Label(); pageTitle.getStyleClass().add("page-title");
        Label pageDetail = new Label("나에게 맞는 플레이 환경을 설정하세요."); pageDetail.getStyleClass().add("muted");
        StackPane page = new StackPane(); page.getStyleClass().add("settings-card");
        VBox.setVgrow(page, Priority.ALWAYS);
        Map<Tab, Node> pages = new LinkedHashMap<>();
        String[] names = {"디스플레이", "오디오", "입력", "라이브러리", "곡 선택", "플레이 옵션", "스킨 및 사운드", "일반", "온라인 순위", "난이도표", "방송"};
        String[] details = {"화면 모드, 해상도와 배경 영상을 설정합니다.", "출력 장치를 선택하고 음량과 지연 시간을 조절합니다.", "키보드, 컨트롤러와 스크래치 입력을 설정합니다.", "곡 폴더와 난이도표를 관리합니다.", "곡 선택 화면의 동작과 미리 듣기를 설정합니다.", "노트 속도, 판정과 플레이 방식을 조절합니다.", "화면 스킨, 배경음과 효과음을 설정합니다.", "편의 기능과 외부 서비스 연결을 관리합니다.", "온라인 순위 서비스와 기록을 연결합니다.", "난이도표의 폴더와 코스를 편집합니다.", "방송용 신청곡 기능과 알림을 설정합니다."};
        String[] icons = {"▣", "♫", "⌘", "▤", "≡", "◈", "◐", "⚙", "◎", "▦", "◉"};
        int index = 0;
        for (Tab tab : tabs.getTabs()) {
            final int i = index++;
            Node content = tab.getContent(); tab.setContent(null); pages.put(tab, content);
            ToggleButton button = new ToggleButton(names[i]);
            Label icon = new Label(icons[i]); icon.setMinWidth(24); icon.getStyleClass().add("nav-icon");
            button.setGraphic(icon); button.setGraphicTextGap(10); button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-item"); button.setToggleGroup(group);
            button.disableProperty().bind(tab.disableProperty());
            button.setAccessibleText(names[i]);
            button.setOnAction(e -> {
                group.selectToggle(button);
                page.getChildren().setAll(pages.get(tab));
                pageTitle.setText(names[i]); pageDetail.setText(details[i]);
                javafx.application.Platform.runLater(() -> { root.applyCss(); AppleTypography.apply(root); });
            });
            KoreanLabels.install(content);
            nav.getChildren().add(button);
        }
        ScrollPane navScroll = new ScrollPane(nav); navScroll.setFitToWidth(true); navScroll.getStyleClass().add("nav-scroll");
        VBox.setVgrow(navScroll, Priority.ALWAYS);
        Label footerBrand = new Label("Mac용 beatoraja"); footerBrand.getStyleClass().add("sidebar-caption");
        VBox sidebar = new VBox(24, brandRow, settingsLabel, navScroll, footerBrand);
        sidebar.setPrefWidth(228); sidebar.setMinWidth(228); sidebar.getStyleClass().add("sidebar");
        profile.getStyleClass().add("profile-bar"); profile.setSpacing(8); profile.setPrefHeight(44);
        for (Node n : profile.getChildren()) HBox.setMargin(n, Insets.EMPTY);
        ((Label)profile.getChildren().get(0)).setMinWidth(Region.USE_PREF_SIZE);
        ((Label)profile.getChildren().get(0)).setText("플레이어");
        profile.getChildren().get(0).getStyleClass().add("eyebrow");
        ComboBox<?> players = (ComboBox<?>)profile.lookup("#players"); players.setPrefWidth(130);
        TextField playername = (TextField)profile.lookup("#playername"); playername.setPrefWidth(180);
        VBox heading = new VBox(7, pageTitle, pageDetail); heading.getStyleClass().add("page-heading");
        update.visibleProperty().bind(update.textProperty().isNotEmpty()); update.managedProperty().bind(update.visibleProperty());
        VBox main = new VBox(18, profile, heading, page, update); main.getStyleClass().add("main-pane");
        BorderPane shell = new BorderPane(); shell.setLeft(sidebar); shell.setCenter(main); VBox.setVgrow(shell, Priority.ALWAYS);
        Button start = (Button) actions.getChildren().get(0);
        Button refresh = (Button) actions.getChildren().get(1);
        Button rebuild = (Button) actions.getChildren().get(2);
        Button exit = (Button) actions.getChildren().get(3);
        for (Node n : actions.getChildren()) { HBox.setMargin(n, Insets.EMPTY); ((Button)n).setPrefWidth(Region.USE_COMPUTED_SIZE); ((Button)n).setPrefHeight(Region.USE_COMPUTED_SIZE); }
        start.setText("플레이  →"); start.getStyleClass().add("play-button"); start.setDefaultButton(true);
        refresh.setText("곡 검색"); rebuild.setText("곡 목록 다시 만들기"); exit.setText("저장 후 닫기");
        Region spring = new Region(); HBox.setHgrow(spring, Priority.ALWAYS);
        actions.getChildren().setAll(refresh, rebuild, spring, exit, start);
        actions.setSpacing(10); actions.setAlignment(Pos.CENTER_LEFT); actions.getStyleClass().add("action-bar"); VBox.setMargin(actions, Insets.EMPTY);
        shell.setBottom(actions); root.getChildren().add(shell);
        ((ToggleButton)nav.getChildren().get(0)).fire();
    }
}
