package com.sakec.smartspace.view;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.service.BookingService;
import com.sakec.smartspace.ui.ComboFilter;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.StatusBadge;
import com.sakec.smartspace.ui.Tables;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** "My bookings" for students and teachers; "Booking requests" (approve / reject) for coordinators. */
public class BookingsPage extends Page {
    private final boolean review;
    private final BookingService service = new BookingService();
    private final ComboFilter<BookingStatus> filter;
    private final VBox tableHost = new VBox();
    private List<Booking> cache = List.of();

    public BookingsPage(boolean review) {
        this.review = review;
        filter = new ComboFilter<>("All statuses", Arrays.asList(BookingStatus.values()), BookingStatus::label);
        if (review) filter.box.getSelectionModel().select(1); // Pending
        filter.onChange(this::render);
        filter.box.setPrefWidth(180);
        HBox bar = new HBox(12, Forms.field("Status", filter.box));
        body.getChildren().addAll(
                review ? Forms.pageHeader("Booking requests", "Approve or reject room requests. Conflicts are re-checked when you approve.")
                        : Forms.pageHeader("My bookings", "Your room requests and their status."),
                bar, Forms.card(null, tableHost));
    }

    @Override public String title() { return review ? "Booking requests" : "My bookings"; }

    @Override public void show() {
        load(() -> {
            service.completePast();
            return review ? service.all() : service.mine();
        }, rows -> { cache = rows; render(); });
    }

    private void render() {
        BookingStatus wanted = filter.selected();
        List<Booking> rows = cache.stream().filter(b -> wanted == null || b.status() == wanted).toList();
        var table = Tables.table(rows, "No bookings here", review ? "New requests will show up here." : "Find a room and send a request to get started.",
                Tables.text("Date", b -> b.start().format(Dt.DATE), 110),
                Tables.text("Time", b -> Dt.range(b.start(), b.end()), 110),
                Tables.text("Room", Booking::roomName, 90),
                Tables.text(review ? "Requested by" : "Note", b -> review ? b.requesterName() : b.reviewNote() == null ? "" : b.reviewNote(), 150),
                Tables.text("Purpose", Booking::purpose, 220),
                Tables.node("Status", b -> StatusBadge.of(b.status()), 120),
                Tables.node("", this::actions, 190));
        tableHost.getChildren().setAll(table);
    }

    private Node actions(Booking b) {
        HBox row = new HBox(8);
        if (review && b.status() == BookingStatus.PENDING) {
            Button approve = Forms.primary("Approve");
            approve.getStyleClass().add("btn-sm");
            approve.setOnAction(e -> act(() -> { service.approve(b.id(), null); return "Request approved."; }));
            Button reject = Forms.danger("Reject");
            reject.getStyleClass().add("btn-sm");
            reject.setOnAction(e -> rejectDialog(b));
            row.getChildren().addAll(approve, reject);
        }
        boolean cancellable = (b.status() == BookingStatus.PENDING || b.status() == BookingStatus.APPROVED)
                && b.end().isAfter(LocalDateTime.now());
        if (cancellable && (!review || b.status() == BookingStatus.APPROVED)) {
            Button cancel = Forms.secondary("Cancel");
            cancel.getStyleClass().add("btn-sm");
            cancel.setOnAction(e -> Modal.confirm("Cancel this booking?", b.roomName() + " on " + b.start().format(Dt.DATE) + ", "
                    + Dt.range(b.start(), b.end()) + " will be released.", "Cancel booking", true,
                    () -> act(() -> { service.cancel(b.id()); return "Booking cancelled."; })));
            row.getChildren().add(cancel);
        }
        return row;
    }

    private void rejectDialog(Booking b) {
        TextArea note = new TextArea();
        note.setPromptText("Optional: tell the requester why (they will see this)");
        note.setPrefRowCount(3);
        note.setWrapText(true);
        Modal m = new Modal("Reject request for " + b.roomName(), Forms.field("Reason", note)).width(440);
        Button cancel = Forms.secondary("Back");
        Button reject = Forms.danger("Reject request");
        cancel.setOnAction(e -> m.close());
        reject.setOnAction(e -> { m.close(); act(() -> { service.reject(b.id(), note.getText()); return "Request rejected."; }); });
        m.actions(cancel, reject).show();
    }
}
