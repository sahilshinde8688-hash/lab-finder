package com.sakec.smartspace.view;

import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.LoadingOverlay;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Errors;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Base class for every screen: a scrolling body, a loading overlay and a helper to load data safely. */
public abstract class Page {
    protected final VBox body = new VBox(20);
    private final StackPane root = new StackPane();
    private final LoadingOverlay loading = new LoadingOverlay();
    private int pending;

    protected Page() {
        body.setPadding(new Insets(24, 28, 32, 28));
        body.getStyleClass().add("page-body");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        root.getChildren().addAll(scroll, loading);
    }

    public Parent root() { return root; }

    public abstract String title();

    /** Called each time the page becomes visible; reload data here. */
    public abstract void show();

    /** Called when the page is replaced so it can stop page-scoped background work. */
    public void hide() { }

    /** Runs work on a background thread with a spinner; shows a friendly toast if it fails. */
    protected <T> void load(Callable<T> work, Consumer<T> onDone) {
        pending++;
        loading.show(true);
        Async.run(work, result -> {
            done();
            onDone.accept(result);
        }, error -> {
            done();
            Toast.error(Errors.message(error));
        });
    }

    /** Runs a write action in the background; refreshes the page afterwards. */
    protected void act(Callable<String> work) {
        pending++;
        loading.show(true);
        Async.run(work, message -> {
            done();
            if (message != null) Toast.success(message);
            show();
        }, error -> {
            done();
            Toast.error(Errors.message(error));
        });
    }

    private void done() {
        if (--pending <= 0) { pending = 0; loading.show(false); }
    }

    protected void setContent(Node... nodes) {
        body.getChildren().setAll(nodes);
        Anim.stagger(body.getChildren());
    }
}
