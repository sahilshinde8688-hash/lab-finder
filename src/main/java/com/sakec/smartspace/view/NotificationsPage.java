package com.sakec.smartspace.view;

import com.sakec.smartspace.app.Events;
import com.sakec.smartspace.model.Notification;
import com.sakec.smartspace.service.NotificationService;
import com.sakec.smartspace.ui.EmptyState;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.ui.Toast;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class NotificationsPage extends Page {
    private final NotificationService service = new NotificationService();
    private final VBox list = new VBox(10);

    public NotificationsPage() {
        Button markAll = Forms.secondary("Mark all as read");
        markAll.setOnAction(e -> Async.run(() -> { service.markAllRead(); return null; },
                v -> { Events.notificationsChanged(); show(); }, ex -> Toast.error(Errors.message(ex))));
        body.getChildren().addAll(Forms.pageHeader("Notifications", "Booking decisions, schedule changes and reports.", markAll), list);
    }

    @Override public String title() { return "Notifications"; }

    @Override public void show() {
        load(() -> service.mine(60), items -> {
            List<javafx.scene.Node> rows = new ArrayList<>();
            for (Notification n : items) rows.add(row(n));
            if (rows.isEmpty()) rows.add(new EmptyState("bell", "You're all caught up", "New notifications will appear here."));
            list.getChildren().setAll(rows);
        });
    }

    private HBox row(Notification n) {
        Label dot = new Label(n.read() ? "" : "●");
        dot.getStyleClass().add("unread-dot");
        dot.setMinWidth(14);
        Label title = new Label(n.title());
        title.getStyleClass().add(n.read() ? "notif-title-read" : "notif-title");
        Label msg = new Label(n.message());
        msg.setWrapText(true);
        msg.getStyleClass().add("muted");
        Label when = new Label(Dt.dateTime(n.createdAt()));
        when.getStyleClass().add("muted-small");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox text = new VBox(3, title, msg);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(10, dot, text, when);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().addAll("card", "notif-row");
        if (!n.read()) {
            row.setOnMouseClicked(e -> Async.run(() -> { service.markRead(n.id()); return null; },
                    v -> { Events.notificationsChanged(); show(); }, ex -> Toast.error(Errors.message(ex))));
        }
        return row;
    }
}
