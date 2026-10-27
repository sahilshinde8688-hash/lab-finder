package com.sakec.smartspace.view;

import com.sakec.smartspace.model.ClassSlot;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.service.BookingService;
import com.sakec.smartspace.service.MaintenanceService;
import com.sakec.smartspace.service.RoomService;
import com.sakec.smartspace.service.TimetableService;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Errors;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Dialogs shared by several pages. */
public final class Dialogs {
    private Dialogs() { }

    // ---- booking form -----------------------------------------------------------------------

    public static void book(Room room, Runnable onDone) {
        BookingService service = new BookingService();
        DatePicker date = new DatePicker(LocalDate.now());
        date.setEditable(false);
        date.setDayCellFactory(p -> new javafx.scene.control.DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setDisable(empty || d.isBefore(LocalDate.now()) || d.isAfter(LocalDate.now().plusDays(60)));
            }
        });
        LocalTime start = Dt.roundUp(LocalTime.now().plusMinutes(30), 15);
        ComboBox<LocalTime> startBox = Forms.timeCombo(start);
        ComboBox<LocalTime> endBox = Forms.timeCombo(start.plusHours(1));
        TextArea purpose = new TextArea();
        purpose.setPromptText("e.g. Group study for the DBMS exam, extra practical session");
        purpose.setPrefRowCount(3);
        purpose.setWrapText(true);
        Label liveCheck = new Label("Checking…");
        liveCheck.getStyleClass().add("muted");
        liveCheck.setWrapText(true);
        Label error = Forms.errorLabel();
        AtomicInteger token = new AtomicInteger();

        Runnable check = () -> {
            int mine = token.incrementAndGet();
            if (date.getValue() == null) return;
            Async.run(() -> service.checkSlot(room.id(), date.getValue(), startBox.getValue(), endBox.getValue()),
                    a -> {
                        if (mine != token.get()) return;
                        boolean free = a.status() == RoomStatus.AVAILABLE;
                        liveCheck.setText(free ? "✓ Free for this slot. " + a.detail() : "✗ " + a.status().label() + ": " + a.detail());
                        liveCheck.getStyleClass().removeAll("ok-text", "error-text", "muted");
                        liveCheck.getStyleClass().add(free ? "ok-text" : "error-text");
                    },
                    ex -> {
                        if (mine != token.get()) return;
                        liveCheck.setText(Errors.message(ex));
                        liveCheck.getStyleClass().removeAll("ok-text", "error-text", "muted");
                        liveCheck.getStyleClass().add("error-text");
                    });
        };
        date.valueProperty().addListener((o, a, b) -> check.run());
        startBox.valueProperty().addListener((o, a, b) -> check.run());
        endBox.valueProperty().addListener((o, a, b) -> check.run());
        check.run();

        Label info = new Label(room.name() + " · Floor " + room.floor() + " · " + room.type().label() + " · " + room.capacity() + " seats");
        info.getStyleClass().add("room-detail");
        HBox times = new HBox(12, Forms.field("From", startBox), Forms.field("To", endBox));
        VBox form = new VBox(14, info, Forms.field("Date", date), times, Forms.field("Purpose", purpose), liveCheck, error);
        Modal m = new Modal("Request " + room.name(), form).width(480);
        Button cancel = Forms.secondary("Cancel");
        Button submit = Forms.primary("Send request");
        cancel.setOnAction(e -> m.close());
        submit.setOnAction(e -> {
            Forms.showError(error, null);
            submit.setDisable(true);
            Async.run(() -> service.request(room.id(), date.getValue(), startBox.getValue(), endBox.getValue(), purpose.getText()),
                    b -> {
                        m.close();
                        Toast.success("Request sent. You'll be notified when it is reviewed.");
                        onDone.run();
                    },
                    ex -> {
                        submit.setDisable(false);
                        Forms.showError(error, Errors.message(ex));
                    });
        });
        m.actions(cancel, submit).show();
    }

    // ---- report problem -----------------------------------------------------------------------

    public static void report(Room preselected, Runnable onDone) {
        ComboBox<Room> roomBox = new ComboBox<>();
        roomBox.setPromptText("Choose a room");
        roomBox.setMaxWidth(Double.MAX_VALUE);
        Async.run(() -> new RoomService().activeRooms(), rooms -> {
            roomBox.getItems().setAll(rooms);
            if (preselected != null) {
                rooms.stream().filter(r -> r.id() == preselected.id()).findFirst().ifPresent(roomBox::setValue);
            }
        }, ex -> Toast.error(Errors.message(ex)));

        ToggleGroup kind = new ToggleGroup();
        RadioButton occupied = new RadioButton("The room is in use but shows as free");
        RadioButton issue = new RadioButton("Equipment or maintenance problem");
        occupied.setToggleGroup(kind);
        issue.setToggleGroup(kind);
        issue.setSelected(true);
        TextArea details = new TextArea();
        details.setPromptText("What did you see? e.g. projector not turning on, a class is in progress");
        details.setPrefRowCount(3);
        details.setWrapText(true);
        Label error = Forms.errorLabel();

        VBox form = new VBox(14, Forms.field("Room", roomBox), new VBox(8, issue, occupied), Forms.field("Details", details), error);
        Modal m = new Modal("Report a problem", form).width(480);
        Button cancel = Forms.secondary("Cancel");
        Button send = Forms.primary("Send report");
        cancel.setOnAction(e -> m.close());
        send.setOnAction(e -> {
            Forms.showError(error, null);
            Room r = roomBox.getValue();
            if (r == null) { Forms.showError(error, "Choose a room."); return; }
            boolean isOccupied = kind.getSelectedToggle() == occupied;
            send.setDisable(true);
            Async.run(() -> {
                MaintenanceService s = new MaintenanceService();
                if (isOccupied) s.reportOccupied(r.id(), details.getText());
                else s.reportIssue(r.id(), details.getText());
                return null;
            }, v -> {
                m.close();
                Toast.success("Thanks — the timetable coordinators have been notified.");
                onDone.run();
            }, ex -> {
                send.setDisable(false);
                Forms.showError(error, Errors.message(ex));
            });
        });
        m.actions(cancel, send).show();
    }

    // ---- room details ---------------------------------------------------------------------------

    public static void roomDetails(RoomAvailability a, Runnable onBook) {
        Room r = a.room();
        VBox box = new VBox(10);
        box.getChildren().addAll(
                line("Location", "Floor " + r.floor() + " · " + r.type().label()),
                line("Capacity", r.capacity() + " seats"),
                line("Facilities", r.facilityList().isEmpty() ? "None listed" : String.join(", ", r.facilityList())),
                line("Right now", a.status().label() + " — " + a.detail()));
        Label today = new Label("Today's classes");
        today.getStyleClass().add("field-label");
        VBox list = new VBox(6, Forms.muted("Loading…"));
        box.getChildren().addAll(today, list);
        Async.run(() -> {
            new RoomService().recordView(r.id());
            return new TimetableService().slotsOn(LocalDate.now()).stream().filter(s -> s.entry().roomId() == r.id()).toList();
        }, (List<ClassSlot> slots) -> {
            list.getChildren().clear();
            if (slots.isEmpty()) list.getChildren().add(Forms.muted("No scheduled classes today."));
            for (ClassSlot s : slots) {
                list.getChildren().add(Forms.muted(Dt.range(s.start(), s.end()) + "  ·  " + s.entry().subject()
                        + " (" + s.entry().className() + ") · " + s.entry().facultyName()));
            }
        }, ex -> list.getChildren().setAll(Forms.muted(Errors.message(ex))));

        Modal m = new Modal(r.name(), box).width(500);
        Button close = Forms.secondary("Close");
        close.setOnAction(e -> m.close());
        if (onBook != null) {
            Button book = Forms.primary("Request this room");
            book.setOnAction(e -> { m.close(); onBook.run(); });
            m.actions(close, book);
        } else {
            m.actions(close);
        }
        m.show();
    }

    private static VBox line(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        Label v = new Label(value);
        v.setWrapText(true);
        return new VBox(2, l, v);
    }

    // ---- add / edit room ------------------------------------------------------------------------

    public static void roomForm(Room existing, Runnable onDone) {
        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText("e.g. 304");
        ComboBox<Integer> floor = new ComboBox<>();
        for (int i = 1; i <= 7; i++) floor.getItems().add(i);
        floor.setValue(existing == null ? 1 : existing.floor());
        ComboBox<RoomType> type = new ComboBox<>();
        type.getItems().addAll(RoomType.values());
        type.setValue(existing == null ? RoomType.CLASSROOM : existing.type());
        TextField capacity = new TextField(existing == null ? "" : String.valueOf(existing.capacity()));
        capacity.setPromptText("Seats");
        capacity.textProperty().addListener((o, a, b) -> { if (!b.matches("\\d{0,4}")) capacity.setText(a); });
        TextField facilities = new TextField(existing == null ? "" : existing.facilities());
        facilities.setPromptText("Comma separated, e.g. Projector, Whiteboard, Air Conditioning");
        Label error = Forms.errorLabel();

        VBox form = new VBox(14, Forms.field("Room name or number", name),
                new HBox(12, Forms.field("Floor", floor), Forms.field("Type", type), Forms.field("Capacity", capacity)),
                Forms.field("Equipment and facilities", facilities), error);
        Modal m = new Modal(existing == null ? "Add room" : "Edit " + existing.name(), form).width(520);
        Button cancel = Forms.secondary("Cancel");
        Button save = Forms.primary("Save room");
        cancel.setOnAction(e -> m.close());
        save.setOnAction(e -> {
            Forms.showError(error, null);
            if (capacity.getText().isBlank()) { Forms.showError(error, "Enter the seating capacity."); return; }
            int cap = Integer.parseInt(capacity.getText());
            save.setDisable(true);
            Async.run(() -> {
                new RoomService().save(existing == null ? null : existing.id(), name.getText(), floor.getValue(),
                        type.getValue(), cap, facilities.getText());
                return null;
            }, v -> { m.close(); Toast.success("Room saved."); onDone.run(); },
                    ex -> { save.setDisable(false); Forms.showError(error, Errors.message(ex)); });
        });
        m.actions(cancel, save).show();
    }

    // ---- change password ------------------------------------------------------------------------

    /** forced = first sign-in with a temporary password; the dialog can't be dismissed except by signing out. */
    public static void changePassword(boolean forced, Runnable onDone, Runnable onSignOut) {
        PasswordField current = new PasswordField();
        PasswordField next = new PasswordField();
        PasswordField confirm = new PasswordField();
        next.setPromptText("At least 10 characters, with letters and numbers");
        Label error = Forms.errorLabel();
        VBox form = new VBox(14);
        if (forced) form.getChildren().add(Forms.muted("You're using a temporary password. Choose your own to continue."));
        form.getChildren().addAll(Forms.field(forced ? "Temporary password" : "Current password", current),
                Forms.field("New password", next), Forms.field("Confirm new password", confirm), error);
        Modal m = new Modal("Change password", form).width(460).dismissable(!forced);
        Button cancel = forced ? Forms.secondary("Sign out") : Forms.secondary("Cancel");
        Button save = Forms.primary("Update password");
        cancel.setOnAction(e -> { m.close(); if (forced) onSignOut.run(); });
        save.setOnAction(e -> {
            Forms.showError(error, null);
            if (!next.getText().equals(confirm.getText())) { Forms.showError(error, "The new passwords don't match."); return; }
            save.setDisable(true);
            Async.run(() -> { new AuthService().changePassword(current.getText(), next.getText()); return null; },
                    v -> { m.close(); Toast.success("Password updated."); onDone.run(); },
                    ex -> { save.setDisable(false); Forms.showError(error, Errors.message(ex)); });
        });
        confirm.setOnAction(e -> save.fire());
        m.actions(cancel, save).show();
        Anim.after(50, current::requestFocus);
    }
}
