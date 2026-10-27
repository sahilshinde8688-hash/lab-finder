package com.sakec.smartspace.view;

import com.sakec.smartspace.model.MaintenanceRecord;
import com.sakec.smartspace.model.MaintenanceStatus;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.service.MaintenanceService;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.StatusBadge;
import com.sakec.smartspace.ui.Tables;
import com.sakec.smartspace.util.Dt;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Reports for everyone (their own); full management for coordinators. */
public class MaintenancePage extends Page {
    private final MaintenanceService service = new MaintenanceService();
    private final boolean manage = Permissions.isCoordinator();
    private final VBox tableHost = new VBox();

    public MaintenancePage() {
        Button report = Forms.withIcon(Forms.primary("Report a problem"), "plus", "#FFFFFF");
        report.setOnAction(e -> Dialogs.report(null, this::show));
        body.getChildren().addAll(Forms.pageHeader(manage ? "Maintenance" : "My reports",
                manage ? "Start work to block a room until it is fixed, then mark it resolved."
                        : "Problems you reported and what happened next.", report),
                Forms.card(null, tableHost));
    }

    @Override public String title() { return manage ? "Maintenance" : "My reports"; }

    @Override public void show() {
        load(service::visible, rows -> tableHost.getChildren().setAll(Tables.table(rows, "Nothing reported",
                "Reported problems will be listed here.",
                Tables.text("Reported", m -> Dt.dateTime(m.start()), 140),
                Tables.text("Room", MaintenanceRecord::roomName, 90),
                Tables.text("Problem", MaintenanceRecord::description, 260),
                Tables.text("Reported by", MaintenanceRecord::reporterName, 140),
                Tables.node("Status", m -> StatusBadge.of(m.status()), 120),
                Tables.text("Blocks room", m -> m.blocksRoom() && m.status() != MaintenanceStatus.RESOLVED ? "Yes" : "No", 90),
                Tables.node("", this::actions, 200))));
    }

    private Node actions(MaintenanceRecord m) {
        HBox row = new HBox(8);
        if (!manage || m.status() == MaintenanceStatus.RESOLVED) return row;
        if (m.status() == MaintenanceStatus.REPORTED) {
            Button start = Forms.secondary("Block room");
            start.getStyleClass().add("btn-sm");
            start.setOnAction(e -> act(() -> { service.startWork(m.id()); return m.roomName() + " is now blocked for maintenance."; }));
            row.getChildren().add(start);
        }
        Button resolve = Forms.primary("Resolve");
        resolve.getStyleClass().add("btn-sm");
        resolve.setOnAction(e -> act(() -> { service.resolve(m.id()); return "Marked as resolved."; }));
        row.getChildren().add(resolve);
        return row;
    }
}
