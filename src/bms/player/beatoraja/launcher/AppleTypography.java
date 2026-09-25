package bms.player.beatoraja.launcher;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Font;

/** Use the exact face: JavaFX 27 currently resolves the family to ExtraBold. */
final class AppleTypography {
    static void apply(Node node) {
        if (node instanceof Labeled label && !label.fontProperty().isBound()) {
            Font current = label.getFont();
            boolean bold = label.getStyleClass().stream().anyMatch(c -> c.equals("page-title") || c.equals("setting-heading") || c.equals("brand-name") || c.equals("play-button"));
            if (!label.getStyleClass().contains("brand-mark") && !label.getStyleClass().contains("brand-name")) {
                label.fontProperty().bind(new SimpleObjectProperty<>(new Font("Apple SD Gothic Neo " + (bold ? "SemiBold" : "Regular"), current.getSize())));
            }
        }
        if (node instanceof TextInputControl input && !input.fontProperty().isBound()) {
            input.fontProperty().bind(new SimpleObjectProperty<>(new Font("Apple SD Gothic Neo Regular", input.getFont().getSize())));
        }
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) apply(child);
    }
}
