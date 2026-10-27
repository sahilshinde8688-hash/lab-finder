package com.sakec.smartspace.ui;

import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.MaintenanceStatus;
import com.sakec.smartspace.model.RoomStatus;
import javafx.scene.control.Label;

/** Coloured pill with a text label, so status never depends on colour alone. */
public final class StatusBadge {
    private StatusBadge() { }

    public static Label of(RoomStatus s) { return make(s.label(), s.css()); }

    public static Label of(BookingStatus s) { return make(s.label(), s.css()); }

    public static Label of(MaintenanceStatus s) { return make(s.label(), s.css()); }

    public static Label make(String text, String css) {
        Label l = new Label("● " + text);
        l.getStyleClass().addAll("badge", css);
        l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return l;
    }
}
