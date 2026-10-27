package com.sakec.smartspace.ui;

import javafx.animation.FadeTransition;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Translucent spinner shown over a page while data loads in the background. */
public class LoadingOverlay extends StackPane {
    public LoadingOverlay() {
        getStyleClass().add("loading-overlay");
        ProgressIndicator p = new ProgressIndicator();
        p.setMaxSize(42, 42);
        getChildren().add(p);
        setVisible(false);
        setMouseTransparent(true);
    }

    public void show(boolean on) {
        if (on) {
            setVisible(true);
            if (!Anim.reduced()) {
                setOpacity(0);
                FadeTransition f = new FadeTransition(Duration.millis(150), this);
                f.setToValue(1);
                f.play();
            }
        } else {
            setVisible(false);
        }
    }
}
