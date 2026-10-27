package com.sakec.smartspace.ui;

import com.sakec.smartspace.util.Async;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Non-blocking notification in the bottom-right corner. */
public final class Toast {
    private Toast() { }

    public static void success(String message) { show(message, "success", "check-circle", "#0E9F6E"); }

    public static void error(String message) { show(message, "error", "alert", "#D14343"); }

    public static void info(String message) { show(message, "info", "bell", "#2F5BEA"); }

    private static void show(String message, String kind, String icon, String color) {
        Async.fx(() -> {
            StackPane host = Overlay.host();
            if (host == null) return;
            Label text = new Label(message);
            text.setWrapText(true);
            text.setMaxWidth(320);
            text.getStyleClass().add("toast-text");
            HBox box = new HBox(10, Icons.of(icon, 18, color), text);
            box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().addAll("toast", "toast-" + kind);
            box.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
            StackPane.setAlignment(box, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(box, new Insets(0, 24, 24, 0));
            host.getChildren().add(box);

            if (!Anim.reduced()) {
                box.setOpacity(0);
                box.setTranslateX(24);
                FadeTransition f = new FadeTransition(Duration.millis(180), box);
                f.setToValue(1);
                TranslateTransition t = new TranslateTransition(Duration.millis(180), box);
                t.setToX(0);
                f.play();
                t.play();
            }
            Runnable remove = () -> {
                if (Anim.reduced()) { host.getChildren().remove(box); return; }
                FadeTransition out = new FadeTransition(Duration.millis(200), box);
                out.setToValue(0);
                out.setOnFinished(e -> host.getChildren().remove(box));
                out.play();
            };
            box.setOnMouseClicked(e -> remove.run());
            PauseTransition wait = new PauseTransition(Duration.seconds("error".equals(kind) ? 5.5 : 3.5));
            wait.setOnFinished(e -> remove.run());
            wait.play();
        });
    }
}
