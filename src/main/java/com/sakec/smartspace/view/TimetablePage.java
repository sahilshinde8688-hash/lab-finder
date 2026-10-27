package com.sakec.smartspace.view;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.service.BookingService;
import com.sakec.smartspace.service.ReportService;
import com.sakec.smartspace.service.RoomService;
import com.sakec.smartspace.service.TimetableService;
import com.sakec.smartspace.ui.ComboFilter;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.TimetableGrid;
import com.sakec.smartspace.ui.TimetableGrid.Block;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Errors;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** Daily and weekly timetable. Coordinators can add, edit and delete entries; everyone else can read. */
public class TimetablePage extends Page {
    private record Data(List<TimetableEntry> entries, List<Room> rooms, List<User> faculty, List<Booking> bookings,
                        List<String> conflicts) { }

    private final boolean edit = Permissions.isCoordinator();
    private final TimetableService service = new TimetableService();
    private final ToggleGroup mode = new ToggleGroup();
    private final ToggleButton dayBtn = new ToggleButton("Day");
    private final ToggleButton weekBtn = new ToggleButton("Week");
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final CheckBox labsOnly = new CheckBox("Labs only");
    private final FlowPane filterRow = new FlowPane(12, 10);
    private final VBox gridHost = new VBox();
    private final VBox conflictHost = new VBox(8);
    private ComboFilter<Integer> floorFilter;
    private ComboFilter<Room> roomFilter;
    private ComboFilter<User> facultyFilter;
    private ComboFilter<String> classFilter;
    private Data cache;

    public TimetablePage() {
        dayBtn.setToggleGroup(mode);
        weekBtn.setToggleGroup(mode);
        dayBtn.getStyleClass().add("seg-btn");
        weekBtn.getStyleClass().add("seg-btn");
        weekBtn.setSelected(true);
        mode.selectedToggleProperty().addListener((o, a, b) -> { if (b == null) a.setSelected(true); else show(); });
        datePicker.setEditable(false);
        datePicker.valueProperty().addListener((o, a, b) -> { if (b != null) show(); });
        labsOnly.selectedProperty().addListener((o, a, b) -> render());

        Button prev = Forms.iconButton("chevron-down", "Previous");
        prev.setRotate(90);
        prev.setOnAction(e -> datePicker.setValue(datePicker.getValue().minusDays(weekBtn.isSelected() ? 7 : 1)));
        Button next = Forms.iconButton("chevron-down", "Next");
        next.setRotate(-90);
        next.setOnAction(e -> datePicker.setValue(datePicker.getValue().plusDays(weekBtn.isSelected() ? 7 : 1)));
        Button today = Forms.secondary("Today");
        today.setOnAction(e -> datePicker.setValue(LocalDate.now()));

        HBox nav = new HBox(8, new HBox(dayBtn, weekBtn), prev, datePicker, next, today);
        nav.setAlignment(Pos.CENTER_LEFT);

        List<javafx.scene.Node> actions = new ArrayList<>();
        if (edit) {
            Button add = Forms.withIcon(Forms.primary("Add entry"), "plus", "#FFFFFF");
            add.setOnAction(e -> entryForm(null));
            Button export = Forms.withIcon(Forms.secondary("Export CSV"), "download", "#0B1F4B");
            export.setOnAction(e -> export());
            actions.add(export);
            actions.add(add);
        }
        body.getChildren().addAll(
                Forms.pageHeader("Timetable", edit ? "Official schedule. Overlaps are highlighted in red; tinted areas are free periods."
                        : "Official schedule (read only). Tinted areas are free periods.", actions.toArray(new javafx.scene.Node[0])),
                nav, filterRow, Forms.card(null, gridHost));
        if (edit) body.getChildren().add(Forms.card("Conflict alerts · next 14 days", conflictHost));
    }

    @Override public String title() { return "Timetable"; }

    private LocalDate[] range() {
        LocalDate d = datePicker.getValue();
        if (dayBtn.isSelected()) return new LocalDate[]{d, d};
        LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{monday, monday.plusDays(5)};
    }

    @Override public void show() {
        LocalDate[] r = range();
        load(() -> new Data(service.all(), new RoomService().activeRooms(), service.faculty(),
                new BookingService().approvedBetween(r[0].atStartOfDay(), r[1].plusDays(1).atStartOfDay()),
                edit ? service.detectConflicts(LocalDate.now(), 14) : List.of()), d -> {
            cache = d;
            if (floorFilter == null) buildFilters(d);
            render();
            conflictHost.getChildren().clear();
            if (d.conflicts().isEmpty()) conflictHost.getChildren().add(Forms.muted("✓ No overlapping rooms, faculty, classes or bookings found."));
            for (String c : d.conflicts().stream().limit(8).toList()) {
                Label l = new Label("⚠  " + c);
                l.getStyleClass().add("error-text");
                conflictHost.getChildren().add(l);
            }
        });
    }

    private void buildFilters(Data d) {
        floorFilter = new ComboFilter<>("All floors", List.of(1, 2, 3, 4, 5, 6, 7), i -> "Floor " + i);
        roomFilter = new ComboFilter<>("All rooms", d.rooms(), Room::name);
        facultyFilter = new ComboFilter<>("All faculty", d.faculty(), User::fullName);
        List<String> classes = new ArrayList<>(new TreeSet<>(d.entries().stream().map(TimetableEntry::className).toList()));
        classFilter = new ComboFilter<>("All classes", classes, s -> s);
        for (ComboFilter<?> f : List.of(floorFilter, roomFilter, facultyFilter, classFilter)) {
            f.box.setPrefWidth(150);
            f.onChange(this::render);
        }
        filterRow.getChildren().addAll(Forms.field("Floor", floorFilter.box), Forms.field("Room", roomFilter.box),
                Forms.field("Faculty", facultyFilter.box), Forms.field("Class", classFilter.box), Forms.field(" ", labsOnly));
    }

    private void render() {
        if (cache == null || floorFilter == null) return;
        Map<Integer, Room> roomById = new HashMap<>();
        cache.rooms().forEach(r -> roomById.put(r.id(), r));
        Integer floor = floorFilter.selected();
        Room room = roomFilter.selected();
        User fac = facultyFilter.selected();
        String cls = classFilter.selected();
        boolean labs = labsOnly.isSelected();

        LocalDate[] r = range();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = r[0]; !d.isAfter(r[1]); d = d.plusDays(1)) days.add(d);
        Map<LocalDate, List<Block>> blocks = new LinkedHashMap<>();
        for (LocalDate d : days) blocks.put(d, new ArrayList<>());

        for (TimetableEntry e : cache.entries()) {
            Room rm = roomById.get(e.roomId());
            if (rm == null) continue;
            if (floor != null && rm.floor() != floor) continue;
            if (room != null && rm.id() != room.id()) continue;
            if (fac != null && e.facultyId() != fac.id()) continue;
            if (cls != null && !e.className().equals(cls)) continue;
            if (labs && !rm.type().isLab()) continue;
            for (LocalDate d : days) {
                if (e.occursOn(d)) blocks.get(d).add(new Block(e.subject(), e.className() + " · " + e.roomName(),
                        e.facultyName(), e.startOn(d), e.endOn(d), false, e));
            }
        }
        if (fac == null && cls == null) {
            for (Booking b : cache.bookings()) {
                Room rm = roomById.get(b.roomId());
                if (rm == null) continue;
                if (floor != null && rm.floor() != floor) continue;
                if (room != null && rm.id() != room.id()) continue;
                if (labs && !rm.type().isLab()) continue;
                LocalDate d = b.start().toLocalDate();
                if (blocks.containsKey(d)) blocks.get(d).add(new Block("Reserved", b.roomName() + " · " + b.purpose(),
                        b.requesterName(), b.start(), b.end(), true, b));
            }
        }
        TimetableGrid grid = new TimetableGrid(days, blocks, this::onBlock);
        grid.setPrefViewportHeight(560);
        grid.setMinHeight(420);
        gridHost.getChildren().setAll(grid);
    }

    private void onBlock(Block b) {
        if (b.ref() instanceof TimetableEntry e) {
            if (edit) entryForm(e);
            else {
                VBox v = new VBox(8, Forms.muted(e.subject() + " · " + e.className()),
                        Forms.muted("Room " + e.roomName() + " · " + e.facultyName()),
                        Forms.muted(Dt.range(b.start(), b.end()) + (e.weekly() ? " · repeats weekly until " + e.until().format(Dt.DATE) : "")),
                        Forms.muted(e.sample() ? "Sample data — not the real college timetable." : ""));
                Modal m = new Modal("Class details", v).width(420);
                Button close = Forms.secondary("Close");
                close.setOnAction(x -> m.close());
                m.actions(close).show();
            }
        } else if (b.ref() instanceof Booking bk) {
            VBox v = new VBox(8, Forms.muted("Approved booking in " + bk.roomName()), Forms.muted(bk.purpose()),
                    Forms.muted("Requested by " + bk.requesterName() + " · " + Dt.range(bk.start(), bk.end())));
            Modal m = new Modal("Reserved", v).width(420);
            Button close = Forms.secondary("Close");
            close.setOnAction(x -> m.close());
            m.actions(close).show();
        }
    }

    private void export() {
        LocalDate[] r = range();
        FileChooser fc = new FileChooser();
        fc.setTitle("Export timetable");
        fc.setInitialFileName("timetable_" + r[0] + "_to_" + r[1] + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showSaveDialog(root().getScene().getWindow());
        if (f == null) return;
        act(() -> { new ReportService().exportTimetable(f.toPath(), r[0], r[1]); return "Timetable exported."; });
    }

    // ---- add / edit form -------------------------------------------------------------------------

    private void entryForm(TimetableEntry existing) {
        if (cache == null) return;
        ComboBox<Room> roomBox = new ComboBox<>();
        roomBox.getItems().addAll(cache.rooms());
        roomBox.setConverter(new StringConverter<>() {
            @Override public String toString(Room r) { return r == null ? "" : r.name() + " · Floor " + r.floor(); }
            @Override public Room fromString(String s) { return null; }
        });
        ComboBox<User> facBox = new ComboBox<>();
        facBox.getItems().addAll(cache.faculty());
        facBox.setConverter(new StringConverter<>() {
            @Override public String toString(User u) { return u == null ? "" : u.fullName(); }
            @Override public User fromString(String s) { return null; }
        });
        roomBox.setMaxWidth(Double.MAX_VALUE);
        facBox.setMaxWidth(Double.MAX_VALUE);
        TextField subject = new TextField(existing == null ? "" : existing.subject());
        TextField className = new TextField(existing == null ? "" : existing.className());
        subject.setPromptText("e.g. Data Structures");
        className.setPromptText("e.g. SE-IT-A");
        DatePicker date = new DatePicker(existing == null ? datePicker.getValue() : existing.start().toLocalDate());
        var start = Forms.timeCombo(existing == null ? LocalTime.of(9, 0) : existing.start().toLocalTime());
        var end = Forms.timeCombo(existing == null ? LocalTime.of(10, 0) : existing.end().toLocalTime());
        CheckBox repeat = new CheckBox("Repeats every week");
        DatePicker until = new DatePicker(existing != null && existing.weekly() ? existing.until() : date.getValue().plusWeeks(15));
        until.disableProperty().bind(repeat.selectedProperty().not());
        repeat.setSelected(existing != null && existing.weekly());
        Label error = Forms.errorLabel();
        if (existing != null) {
            cache.rooms().stream().filter(r -> r.id() == existing.roomId()).findFirst().ifPresent(roomBox::setValue);
            cache.faculty().stream().filter(u -> u.id() == existing.facultyId()).findFirst().ifPresent(facBox::setValue);
        }
        VBox form = new VBox(14, Forms.field("Room", roomBox), Forms.field("Subject", subject), Forms.field("Class / batch", className),
                Forms.field("Faculty", facBox),
                new HBox(12, Forms.field("Date (first lecture)", date), Forms.field("From", start), Forms.field("To", end)),
                new HBox(12, repeat, Forms.field("Until", until)), error);
        Modal m = new Modal(existing == null ? "Add timetable entry" : "Edit timetable entry", form).width(560);
        Button cancel = Forms.secondary("Cancel");
        Button save = Forms.primary("Save entry");
        cancel.setOnAction(e -> m.close());
        save.setOnAction(e -> {
            Forms.showError(error, null);
            if (roomBox.getValue() == null || facBox.getValue() == null) { Forms.showError(error, "Choose a room and a faculty member."); return; }
            save.setDisable(true);
            Async.run(() -> {
                service.save(existing == null ? null : existing.id(), roomBox.getValue().id(), subject.getText(), className.getText(),
                        facBox.getValue().id(), date.getValue(), start.getValue(), end.getValue(), repeat.isSelected(), until.getValue());
                return null;
            }, v -> { m.close(); Toast.success("Timetable updated."); show(); },
                    ex -> { save.setDisable(false); Forms.showError(error, Errors.message(ex)); });
        });
        if (existing != null) {
            Button del = Forms.danger("Delete");
            del.setOnAction(e -> Modal.confirm("Delete this entry?", existing.subject() + " (" + existing.className()
                    + ") will be removed from every date it covers.", "Delete", true, () -> {
                m.close();
                act(() -> { service.delete(existing.id()); return "Entry deleted."; });
            }));
            m.actions(del, cancel, save);
        } else {
            m.actions(cancel, save);
        }
        m.show();
    }
}
