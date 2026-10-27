package com.sakec.smartspace.view;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.ClassSlot;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.service.BookingService;
import com.sakec.smartspace.service.DashboardService;
import com.sakec.smartspace.service.DashboardService.DashboardData;
import com.sakec.smartspace.service.ReportService;
import com.sakec.smartspace.service.ReportService.Report;
import com.sakec.smartspace.service.ReportService.RoomUtil;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.EmptyState;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.RoomCard;
import com.sakec.smartspace.ui.StatCard;
import com.sakec.smartspace.ui.StatusBadge;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** One class, three layouts: student, teacher and timetable coordinator dashboards. */
public class DashboardPage extends Page {
    private record Bundle(DashboardData data, Report report) { }

    private final Navigator nav;
    private final User me = Session.user();
    private final DashboardService service = new DashboardService();

    public DashboardPage(Navigator nav) { this.nav = nav; }

    @Override public String title() { return "Dashboard"; }

    @Override public void show() {
        load(() -> {
            DashboardData d = service.load();
            Report r = me.role() == Role.COORDINATOR ? new ReportService().build(LocalDate.now().minusDays(6), LocalDate.now()) : null;
            new BookingService().completePast();
            return new Bundle(d, r);
        }, b -> {
            switch (me.role()) {
                case STUDENT -> student(b.data());
                case TEACHER -> teacher(b.data());
                case COORDINATOR -> coordinator(b.data(), b.report());
            }
        });
    }

    // ---- student -----------------------------------------------------------------------------

    private void student(DashboardData d) {
        HBox stats = statRow(new StatCard("layers", "Rooms and labs", String.valueOf(d.totalRooms()), "navy"),
                new StatCard("grid", "Classrooms", String.valueOf(d.classrooms()), "blue"),
                new StatCard("tool", "Laboratories", String.valueOf(d.labs()), "teal"),
                new StatCard("check-circle", "Available now", String.valueOf(d.availableNow()), "teal"));

        TextField quick = new TextField();
        quick.setPromptText("Quick search: room name, type or equipment");
        HBox.setHgrow(quick, Priority.ALWAYS);
        Button advanced = Forms.secondary("Filters & other times");
        advanced.setOnAction(e -> nav.go("rooms"));
        HBox searchRow = new HBox(10, quick, advanced);

        FlowPane free = new FlowPane(14, 14);
        Runnable fill = () -> {
            String q = quick.getText() == null ? "" : quick.getText().trim().toLowerCase();
            free.getChildren().clear();
            List<RoomAvailability> shown = d.nowStatus().stream().filter(a -> a.status() == RoomStatus.AVAILABLE)
                    .filter(a -> q.isEmpty() || (a.room().name() + " " + a.room().type().label() + " " + a.room().facilities()).toLowerCase().contains(q))
                    .limit(8).toList();
            if (shown.isEmpty()) free.getChildren().add(new EmptyState("search", "No free rooms match", "Try a different search or check other times."));
            for (RoomAvailability a : shown) free.getChildren().add(roomCard(a));
        };
        quick.textProperty().addListener((o, a, b) -> fill.run());
        fill.run();

        VBox right = new VBox(16, upcoming("Upcoming classes today", d.todayAll()), recentRooms(d.recentRooms()),
                bookingList(d.recentBookings()), quickActions());
        right.setPrefWidth(370);
        right.setMinWidth(300);
        VBox left = new VBox(16, Forms.card("Available right now", new VBox(14, searchRow, free)));
        HBox.setHgrow(left, Priority.ALWAYS);
        setContent(welcome(d), stats, new HBox(18, left, right));
    }

    // ---- teacher -----------------------------------------------------------------------------

    private void teacher(DashboardData d) {
        LocalDateTime now = LocalDateTime.now();
        ClassSlot next = d.todayMine().stream().filter(s -> s.end().isAfter(now)).findFirst().orElse(null);
        HBox stats = statRow(new StatCard("calendar", "My classes today", String.valueOf(d.todayMine().size()), "navy"),
                new StatCard("clock", "Next class", next == null ? "None" : next.start().format(Dt.TIME), "blue"),
                new StatCard("check-circle", "Rooms free now", String.valueOf(d.availableNow()), "teal"),
                new StatCard("inbox", "My pending requests", String.valueOf(d.myPending()), "amber"));

        VBox rooms = new VBox(8);
        if (d.assignedRooms().isEmpty()) rooms.getChildren().add(Forms.muted("Rooms from your timetable will appear here."));
        for (Room r : d.assignedRooms()) rooms.getChildren().add(new Label(r.name() + " · Floor " + r.floor() + " · " + r.type().label()));

        Button find = Forms.withIcon(Forms.primary("Find or request a room"), "search", "#FFFFFF");
        find.setOnAction(e -> nav.go("rooms"));
        VBox right = new VBox(16, Forms.card("My rooms", rooms), bookingList(d.recentBookings()), Forms.card("Quick actions", new VBox(10, find, reportButton())));
        right.setPrefWidth(370);
        right.setMinWidth(300);
        VBox left = new VBox(16, upcoming("My timetable today", d.todayMine()), Forms.card("Available rooms by floor", floorBars(d.floors())));
        HBox.setHgrow(left, Priority.ALWAYS);
        setContent(welcome(d), stats, new HBox(18, left, right));
    }

    // ---- coordinator -------------------------------------------------------------------------

    private void coordinator(DashboardData d, Report r) {
        HBox row1 = statRow(new StatCard("layers", "Total rooms", String.valueOf(d.totalRooms()), "navy"),
                new StatCard("grid", "Classrooms", String.valueOf(d.classrooms()), "blue"),
                new StatCard("tool", "Laboratories", String.valueOf(d.labs()), "teal"),
                new StatCard("calendar", "Classes today", String.valueOf(d.todayAll().size()), "navy"));
        HBox row2 = statRow(new StatCard("check-circle", "Available now", String.valueOf(d.availableNow()), "teal"),
                new StatCard("users", "Occupied / reserved", String.valueOf(d.occupiedNow()), "red"),
                new StatCard("inbox", "Pending requests", String.valueOf(d.pendingRequests()), "amber"),
                new StatCard("alert", "Open maintenance", String.valueOf(d.openMaintenance()), "amber"));

        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis(0, 100, 20);
        y.setLabel("Utilization %");
        y.setMinorTickVisible(false);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(!Anim.reduced());
        chart.setPrefHeight(280);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (RoomUtil u : r.utilization().stream().limit(8).toList()) {
            s.getData().add(new XYChart.Data<>(u.room().name(), Math.round(u.percent() * 10) / 10.0));
        }
        chart.getData().add(s);
        Button more = Forms.secondary("Full analytics");
        more.setOnAction(e -> nav.go("analytics"));
        VBox utilCard = Forms.card("Room utilization", new VBox(10, chart, more));

        VBox alerts = new VBox(8);
        if (d.conflicts().isEmpty()) alerts.getChildren().add(Forms.muted("✓ No conflicts in the next 7 days."));
        for (String c : d.conflicts().stream().limit(5).toList()) {
            Label l = new Label("⚠  " + c);
            l.getStyleClass().add("error-text");
            l.setWrapText(true);
            alerts.getChildren().add(l);
        }
        Button review = Forms.primary("Review requests");
        review.setOnAction(e -> nav.go("requests"));
        Button tt = Forms.secondary("Open timetable");
        tt.setOnAction(e -> nav.go("timetable"));
        VBox right = new VBox(16, Forms.card("Conflict alerts", alerts),
                Forms.card("Floors · free now", floorBars(d.floors())),
                Forms.card("Quick actions", new HBox(10, review, tt)));
        right.setPrefWidth(360);
        right.setMinWidth(320);
        VBox left = new VBox(16, utilCard, upcoming("Today's scheduled classes", d.todayAll()));
        HBox.setHgrow(left, Priority.ALWAYS);
        setContent(welcome(d), row1, row2, new HBox(18, left, right));
    }

    // ---- shared pieces -----------------------------------------------------------------------

    private Node welcome(DashboardData d) {
        int h = LocalDateTime.now().getHour();
        String greet = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        Label hello = new Label(greet + ", " + me.firstName());
        hello.getStyleClass().add("page-heading");
        String who = me.role().label() + (me.department() == null || me.department().isBlank() ? "" : " · " + me.department());
        Label sub = new Label(who + " · " + LocalDate.now().format(Dt.LONG_DATE));
        sub.getStyleClass().add("muted");
        return new VBox(4, hello, sub);
    }

    private HBox statRow(StatCard... cards) {
        HBox row = new HBox(16, cards);
        for (StatCard c : cards) { HBox.setHgrow(c, Priority.ALWAYS); c.setMaxWidth(Double.MAX_VALUE); c.setMinWidth(170); }
        return row;
    }

    private RoomCard roomCard(RoomAvailability a) {
        Button details = Forms.secondary("Details");
        details.getStyleClass().add("btn-sm");
        details.setOnAction(e -> Dialogs.roomDetails(a, () -> Dialogs.book(a.room(), this::show)));
        Button request = Forms.primary("Request");
        request.getStyleClass().add("btn-sm");
        request.setOnAction(e -> Dialogs.book(a.room(), this::show));
        RoomCard c = new RoomCard(a, List.of(details, request));
        c.setPrefWidth(260);
        return c;
    }

    private VBox upcoming(String title, List<ClassSlot> slots) {
        LocalDateTime now = LocalDateTime.now();
        VBox list = new VBox(10);
        List<ClassSlot> shown = slots.stream().filter(s -> s.end().isAfter(now)).limit(7).toList();
        if (shown.isEmpty()) list.getChildren().add(Forms.muted("No more classes scheduled today."));
        for (ClassSlot s : shown) {
            boolean live = !s.start().isAfter(now);
            Label time = new Label(s.start().format(Dt.TIME) + " – " + s.end().format(Dt.TIME));
            time.getStyleClass().add("time-pill");
            time.setMinWidth(Region.USE_PREF_SIZE);
            Label subj = new Label(s.entry().subject() + (live ? "   · in progress" : ""));
            subj.getStyleClass().add("list-title");
            Label meta = new Label(s.entry().className() + " · " + s.entry().roomName() + " · " + s.entry().facultyName());
            meta.getStyleClass().add("muted-small");
            HBox row = new HBox(12, time, new VBox(2, subj, meta));
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        return Forms.card(title, list);
    }

    private VBox recentRooms(List<Room> rooms) {
        VBox list = new VBox(6);
        if (rooms.isEmpty()) list.getChildren().add(Forms.muted("Rooms you open will be listed here."));
        for (Room r : rooms) list.getChildren().add(new Label(r.name() + " · Floor " + r.floor() + " · " + r.type().label()));
        return Forms.card("Recently viewed", list);
    }

    private VBox bookingList(List<Booking> bookings) {
        VBox list = new VBox(10);
        if (bookings.isEmpty()) list.getChildren().add(Forms.muted("No booking requests yet."));
        for (Booking b : bookings) {
            Label l = new Label(b.roomName() + " · " + b.start().format(Dt.DATE) + " " + b.start().format(Dt.TIME));
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox row = new HBox(8, l, sp, StatusBadge.of(b.status()));
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        Button all = Forms.ghost("View all");
        all.setOnAction(e -> nav.go("bookings"));
        list.getChildren().add(all);
        return Forms.card("My booking requests", list);
    }

    private VBox quickActions() {
        Button find = Forms.withIcon(Forms.primary("Find a room"), "search", "#FFFFFF");
        find.setOnAction(e -> nav.go("rooms"));
        return Forms.card("Quick actions", new VBox(10, find, reportButton()));
    }

    private Button reportButton() {
        Button report = Forms.withIcon(Forms.secondary("Report occupied room / issue"), "flag", "#0B1F4B");
        report.setOnAction(e -> Dialogs.report(null, () -> { }));
        return report;
    }

    private VBox floorBars(Map<Integer, int[]> floors) {
        VBox box = new VBox(10);
        floors.forEach((f, c) -> {
            Label name = new Label("Floor " + f);
            name.setMinWidth(60);
            ProgressBar bar = new ProgressBar(c[1] == 0 ? 0 : c[0] / (double) c[1]);
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            Label txt = new Label(c[1] == 0 ? "no rooms" : c[0] + "/" + c[1] + " free");
            txt.getStyleClass().add("muted-small");
            txt.setMinWidth(70);
            HBox row = new HBox(10, name, bar, txt);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        });
        return box;
    }
}
