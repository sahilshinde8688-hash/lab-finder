package com.sakec.smartspace.view;

import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.SearchCriteria;
import com.sakec.smartspace.service.AvailabilityService;
import com.sakec.smartspace.service.ReportService;
import com.sakec.smartspace.service.RoomService;
import com.sakec.smartspace.ui.EmptyState;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.RoomCard;
import com.sakec.smartspace.ui.RoomSearchPanel;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.ui.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

/** Find-a-room for students and teachers; the same screen becomes "Rooms" management for coordinators. */
public class RoomsPage extends Page {
    private record Result(List<RoomAvailability> rooms, Map<Integer, int[]> floors) { }

    private final boolean manage;
    private final RoomSearchPanel panel;
    private final Label count = new Label();
    private final FlowPane grid = new FlowPane(16, 16);
    private final AvailabilityService availability = new AvailabilityService();
    private final ReportService reports = new ReportService();

    public RoomsPage(boolean manage) {
        this.manage = manage;
        panel = new RoomSearchPanel(manage);
        panel.setOnSearch(this::show);
        count.getStyleClass().add("muted");
        Button add = Forms.withIcon(Forms.primary("Add room"), "plus", "#FFFFFF");
        add.setOnAction(e -> Dialogs.roomForm(null, this::show));
        Button report = Forms.secondary("Report a problem");
        report.setOnAction(e -> Dialogs.report(null, () -> { }));
        body.getChildren().addAll(
                manage ? Forms.pageHeader("Rooms", "Add, edit and deactivate rooms across all seven floors.", add)
                        : Forms.pageHeader("Find a room", "Search the seven floors by time, type, capacity and equipment.", report),
                panel, count, grid);
    }

    @Override public String title() { return manage ? "Rooms" : "Find a room"; }

    @Override public void show() {
        SearchCriteria c;
        try {
            c = panel.criteria();
        } catch (AppException e) {
            Toast.error(e.getMessage());
            return;
        }
        load(() -> new Result(availability.search(c), reports.floorSummaryNow()), r -> {
            panel.updateFloorCounts(r.floors());
            String when = c.to().isAfter(c.from().plusMinutes(2))
                    ? Dt.dateTime(c.from()) + " – " + c.to().format(Dt.TIME) : "right now";
            count.setText(r.rooms().size() + (r.rooms().size() == 1 ? " room" : " rooms") + " · availability for " + when);
            grid.getChildren().clear();
            if (r.rooms().isEmpty()) {
                grid.getChildren().add(new EmptyState("search", "No rooms match",
                        "Try another floor, a different time, or fewer filters."));
            }
            List<RoomCard> cards = new ArrayList<>();
            for (RoomAvailability a : r.rooms()) cards.add(card(a));
            grid.getChildren().addAll(cards);
            Anim.stagger(cards);
        });
    }

    private RoomCard card(RoomAvailability a) {
        Button details = Forms.secondary("Details");
        details.getStyleClass().add("btn-sm");
        List<Button> actions = new ArrayList<>();
        if (manage) {
            Button edit = Forms.secondary("Edit");
            edit.getStyleClass().add("btn-sm");
            edit.setOnAction(e -> Dialogs.roomForm(a.room(), this::show));
            boolean active = a.room().active();
            Button toggle = active ? Forms.danger("Deactivate") : Forms.primary("Activate");
            toggle.getStyleClass().add("btn-sm");
            toggle.setOnAction(e -> {
                if (active) {
                    Modal.confirm("Deactivate " + a.room().name() + "?",
                            "The room will no longer appear in searches or accept bookings. Existing records are kept.",
                            "Deactivate", true, () -> act(() -> { new RoomService().setActive(a.room().id(), false); return "Room deactivated."; }));
                } else {
                    act(() -> { new RoomService().setActive(a.room().id(), true); return "Room activated."; });
                }
            });
            details.setOnAction(e -> Dialogs.roomDetails(a, null));
            actions.add(details);
            actions.add(edit);
            actions.add(toggle);
        } else {
            Button request = Forms.primary("Request");
            request.getStyleClass().add("btn-sm");
            request.setOnAction(e -> Dialogs.book(a.room(), this::show));
            Button report = Forms.ghost("Report");
            report.getStyleClass().add("btn-sm");
            report.setOnAction(e -> Dialogs.report(a.room(), () -> { }));
            details.setOnAction(e -> Dialogs.roomDetails(a, () -> Dialogs.book(a.room(), this::show)));
            actions.add(details);
            actions.add(request);
            actions.add(report);
        }
        return new RoomCard(a, actions);
    }
}
