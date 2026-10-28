package com.sakec.smartspace.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Number + caption + icon. Accent is one of: navy, blue, teal, amber, red. */
public class StatCard extends HBox {
    private final Label value = new Label("–");

    public StatCard(String icon, String caption, String initialValue, String accent) {
        super(14);
        getStyleClass().addAll("card", "stat-card");
        setAlignment(Pos.CENTER_LEFT);
        StackPane chip = new StackPane(Icons.of(icon, 20, colorFor(accent)));
        chip.getStyleClass().addAll("stat-icon", "stat-" + accent);
        value.getStyleClass().add("stat-value");
        value.setText(initialValue);
        Label cap = new Label(caption);
        cap.getStyleClass().add("stat-caption");
        cap.setWrapText(true);
        cap.setMinWidth(0);
        VBox text = new VBox(2, value, cap);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        getChildren().addAll(chip, text);
        setMinWidth(0);
        Anim.hoverLift(this);
    }

    public void setValue(String text) { value.setText(text); }

    private static String colorFor(String accent) {
        return switch (accent) {
            case "teal" -> "#0E8F7B";
            case "amber" -> "#B7791F";
            case "red" -> "#C0392B";
            case "navy" -> "#0B1F4B";
            default -> "#2F5BEA";
        };
    }
}
