package com.sakec.smartspace.ui;

import javafx.scene.layout.StackPane;

/** The StackPane at the root of the current screen. Toasts and dialogs are drawn on top of it. */
public final class Overlay {
    private static StackPane host;

    private Overlay() { }

    public static void setHost(StackPane pane) { host = pane; }

    public static StackPane host() { return host; }
}
