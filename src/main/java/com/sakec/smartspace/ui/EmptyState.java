package com.sakec.smartspace.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Friendly placeholder shown when a list has nothing to display. */
public class EmptyState extends VBox {
    public EmptyState(String icon, String title, String message) {
        super(8);
        setAlignment(Pos.CENTER);
        getStyleClass().add("empty-state");
        StackPane chip = new StackPane(Icons.of(icon, 26, "#2F5BEA"));
        chip.getStyleClass().add("empty-icon");
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        Label m = new Label(message);
        m.getStyleClass().add("muted");
        m.setWrapText(true);
        m.setMaxWidth(360);
        m.setAlignment(Pos.CENTER);
        m.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        getChildren().addAll(chip, t, m);
    }
}
