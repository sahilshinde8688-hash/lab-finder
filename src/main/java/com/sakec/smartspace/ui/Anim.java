package com.sakec.smartspace.ui;

import com.sakec.smartspace.util.Config;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/** Small, optional animations. Everything is skipped when reduced motion is on. */
public final class Anim {
    private Anim() { }

    public static boolean reduced() {
        return Config.getBool("ui.reducedMotion", false) || Boolean.getBoolean("smartspace.reducedMotion");
    }

    public static void fadeIn(Node n) { fadeIn(n, 0); }

    public static void fadeIn(Node n, int delayMs) {
        if (reduced()) { n.setOpacity(1); return; }
        n.setOpacity(0);
        n.setTranslateY(8);
        FadeTransition f = new FadeTransition(Duration.millis(240), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(240), n);
        t.setToY(0);
        ParallelTransition p = new ParallelTransition(f, t);
        p.setDelay(Duration.millis(delayMs));
        p.play();
    }

    public static void stagger(List<? extends Node> nodes) {
        int i = 0;
        for (Node n : nodes) fadeIn(n, Math.min(i++, 8) * 35);
    }

    /** Gentle lift on hover for interactive cards. */
    public static void hoverLift(Node n) {
        if (reduced()) return;
        n.setOnMouseEntered(e -> lift(n, 1.012, -2));
        n.setOnMouseExited(e -> lift(n, 1.0, 0));
    }

    private static void lift(Node n, double scale, double y) {
        ScaleTransition s = new ScaleTransition(Duration.millis(120), n);
        s.setToX(scale);
        s.setToY(scale);
        TranslateTransition t = new TranslateTransition(Duration.millis(120), n);
        t.setToY(y);
        new ParallelTransition(s, t).play();
    }

    /** Short "pop" used as feedback after a successful action. */
    public static void pulse(Node n) {
        if (reduced()) return;
        ScaleTransition s = new ScaleTransition(Duration.millis(140), n);
        s.setToX(1.06);
        s.setToY(1.06);
        s.setAutoReverse(true);
        s.setCycleCount(2);
        s.play();
    }

    public static void after(int ms, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> r.run());
        p.play();
    }
}
