package com.sakec.smartspace.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** In-window dialog with a dimmed backdrop. Used for forms and confirmations. */
public final class Modal {
    private ScrollPane scrollRef;
    private Node bodyRef;
    private final StackPane scrim = new StackPane();
    private final VBox card = new VBox(16);
    private final HBox footer = new HBox(10);
    private boolean dismissable = true;
    private Runnable onClose = () -> { };

    public Modal(String title, Node body) {
        Label heading = new Label(title);
        heading.getStyleClass().add("modal-title");
        Button close = Forms.iconButton("x", "Close");
        close.setOnAction(e -> close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(heading, spacer, close);
        head.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("modal-scroll");
        scrollRef = scroll;
        bodyRef = body;
        scroll.setPrefViewportHeight(470);
        body.layoutBoundsProperty().addListener((o, a, b) -> scroll.setPrefViewportHeight(Math.min(470, Math.max(40, b.getHeight() + 6))));
        VBox.setVgrow(scroll, Priority.SOMETIMES);

        footer.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(head, scroll, footer);
        card.getStyleClass().add("modal-card");
        card.setPadding(new Insets(22));
        card.setMaxWidth(520);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setOnMouseClicked(e -> e.consume());

        scrim.getStyleClass().add("scrim");
        scrim.getChildren().add(card);
        scrim.setOnMouseClicked(e -> { if (dismissable) close(); });
        scrim.setOnKeyPressed(e -> { if (dismissable && e.getCode() == KeyCode.ESCAPE) close(); });
    }

    public Modal width(double w) { card.setMaxWidth(w); card.setPrefWidth(w); return this; }

    public Modal actions(Node... nodes) { footer.getChildren().setAll(nodes); return this; }

    public Modal dismissable(boolean value) { dismissable = value; return this; }

    public Modal onClose(Runnable r) { onClose = r; return this; }

    public Modal show() {
        StackPane host = Overlay.host();
        if (host == null) return this;
        host.getChildren().add(scrim);
        scrim.requestFocus();
        if (!Anim.reduced()) {
            scrim.setOpacity(0);
            card.setScaleX(0.96);
            card.setScaleY(0.96);
            FadeTransition f = new FadeTransition(Duration.millis(160), scrim);
            f.setToValue(1);
            ScaleTransition s = new ScaleTransition(Duration.millis(160), card);
            s.setToX(1);
            s.setToY(1);
            new ParallelTransition(f, s).play();
        }
        return this;
    }

    public void close() {
        StackPane host = Overlay.host();
        if (host == null || !host.getChildren().contains(scrim)) return;
        if (Anim.reduced()) {
            host.getChildren().remove(scrim);
            onClose.run();
            return;
        }
        FadeTransition f = new FadeTransition(Duration.millis(120), scrim);
        f.setToValue(0);
        f.setOnFinished(e -> { host.getChildren().remove(scrim); onClose.run(); });
        f.play();
    }

    /** Yes/No style confirmation. */
    public static void confirm(String title, String message, String confirmText, boolean danger, Runnable onConfirm) {
        Label text = new Label(message);
        text.setWrapText(true);
        text.getStyleClass().add("muted");
        Modal m = new Modal(title, text).width(420);
        Button cancel = Forms.secondary("Cancel");
        Button ok = danger ? Forms.danger(confirmText) : Forms.primary(confirmText);
        cancel.setOnAction(e -> m.close());
        ok.setOnAction(e -> { m.close(); onConfirm.run(); });
        m.actions(cancel, ok).show();
    }
}
