package com.sakec.smartspace.ui;

import java.time.LocalTime;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Factory methods so every button, field and card looks the same. */
public final class Forms {
    private Forms() { }

    public static Button primary(String text) { return button(text, "btn-primary"); }

    public static Button secondary(String text) { return button(text, "btn-secondary"); }

    public static Button danger(String text) { return button(text, "btn-danger"); }

    public static Button ghost(String text) { return button(text, "btn-ghost"); }

    private static Button button(String text, String style) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", style);
        b.setMnemonicParsing(false);
        return b;
    }

    public static Button withIcon(Button b, String icon, String colorHex) {
        b.setGraphic(Icons.of(icon, 16, colorHex));
        b.setGraphicTextGap(8);
        return b;
    }

    public static Button iconButton(String icon, String tip) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 18, "#66728A"));
        b.getStyleClass().add("icon-btn");
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(true);
        return b;
    }

    public static VBox field(String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(6, l, input);
        if (input instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    public static Label errorLabel() {
        Label l = new Label();
        l.getStyleClass().add("error-text");
        l.setWrapText(true);
        l.setManaged(false);
        l.setVisible(false);
        return l;
    }

    public static void showError(Label l, String message) {
        l.setText(message);
        l.setManaged(message != null);
        l.setVisible(message != null);
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    public static ComboBox<LocalTime> timeCombo(LocalTime initial) {
        ComboBox<LocalTime> box = new ComboBox<>();
        for (LocalTime t = LocalTime.of(7, 0); !t.isAfter(LocalTime.of(21, 0)); t = t.plusMinutes(15)) box.getItems().add(t);
        box.setConverter(new StringConverter<>() {
            @Override public String toString(LocalTime t) { return t == null ? "" : t.toString(); }
            @Override public LocalTime fromString(String s) { return s == null || s.isBlank() ? null : LocalTime.parse(s); }
        });
        LocalTime pick = initial;
        if (pick == null || !box.getItems().contains(pick)) pick = LocalTime.of(9, 0);
        box.setValue(pick);
        box.setPrefWidth(110);
        return box;
    }

    /** Page title row with optional action buttons on the right. */
    public static HBox pageHeader(String title, String subtitle, Node... actions) {
        Label t = new Label(title);
        t.getStyleClass().add("page-heading");
        Label s = new Label(subtitle);
        s.getStyleClass().add("muted");
        VBox left = new VBox(4, t, s);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, left, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        for (Node a : actions) if (a instanceof Button b) b.setMinWidth(Region.USE_PREF_SIZE);
        s.setWrapText(true);
        HBox.setHgrow(left, Priority.ALWAYS);
        row.getChildren().addAll(actions);
        return row;
    }

    /** White card with an optional title. */
    public static VBox card(String title, Node content) {
        VBox box = new VBox(14);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(18));
        if (title != null) {
            Label l = new Label(title);
            l.getStyleClass().add("card-title");
            box.getChildren().add(l);
        }
        box.getChildren().add(content);
        return box;
    }
}
