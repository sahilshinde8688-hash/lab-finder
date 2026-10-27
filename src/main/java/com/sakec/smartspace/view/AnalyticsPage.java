package com.sakec.smartspace.view;

import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.MaintenanceRecord;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.service.ReportService;
import com.sakec.smartspace.service.ReportService.Report;
import com.sakec.smartspace.service.ReportService.RoomUtil;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.StatCard;
import com.sakec.smartspace.ui.StatusBadge;
import com.sakec.smartspace.ui.Tables;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Errors;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Utilization, usage, approval statistics and maintenance records, all calculated from the database. */
public class AnalyticsPage extends Page {
    private final ReportService reports = new ReportService();
    private final DatePicker from = new DatePicker(LocalDate.now().minusDays(29));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final VBox content = new VBox(20);
    private Report current;

    public AnalyticsPage() {
        from.setEditable(false);
        to.setEditable(false);
        Button apply = Forms.primary("Apply");
        apply.setOnAction(e -> show());
        Button expUtil = Forms.withIcon(Forms.secondary("Utilization CSV"), "download", "#0B1F4B");
        expUtil.setOnAction(e -> export("utilization", (f, r) -> reports.exportUtilization(f, r)));
        Button expBook = Forms.withIcon(Forms.secondary("Bookings CSV"), "download", "#0B1F4B");
        expBook.setOnAction(e -> export("bookings", (f, r) -> reports.exportBookings(f, r.from(), r.to())));
        Button expTt = Forms.withIcon(Forms.secondary("Timetable CSV"), "download", "#0B1F4B");
        expTt.setOnAction(e -> export("timetable", (f, r) -> reports.exportTimetable(f, r.from(), r.to())));
        HBox filters = new HBox(12, Forms.field("From", from), Forms.field("To", to), Forms.field(" ", apply));
        filters.setAlignment(Pos.BOTTOM_LEFT);
        body.getChildren().addAll(Forms.pageHeader("Analytics", "Real figures from saved timetable, bookings and maintenance records.", expUtil, expBook, expTt),
                filters, content);
    }

    @Override public String title() { return "Analytics"; }

    @Override public void show() {
        LocalDate f = from.getValue(), t = to.getValue();
        load(() -> reports.build(f, t), this::render);
    }

    private interface Exporter { void run(java.nio.file.Path file, Report r); }

    private void export(String name, Exporter ex) {
        if (current == null) { Toast.error("Wait for the report to finish loading."); return; }
        Report r = current;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export " + name);
        fc.setInitialFileName(name + "_" + r.from() + "_to_" + r.to() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = fc.showSaveDialog(root().getScene().getWindow());
        if (file == null) return;
        act(() -> { ex.run(file.toPath(), r); return "Exported " + name + " CSV."; });
    }

    private void render(Report r) {
        current = r;
        int total = r.bookingStats().values().stream().mapToInt(Integer::intValue).sum();
        int approved = r.bookingStats().get(BookingStatus.APPROVED) + r.bookingStats().get(BookingStatus.COMPLETED);
        int rejected = r.bookingStats().get(BookingStatus.REJECTED);
        String rate = approved + rejected == 0 ? "–" : Math.round(approved * 100.0 / (approved + rejected)) + "%";

        HBox stats = new HBox(16,
                new StatCard("chart", "Overall room utilization", String.format("%.1f%%", r.overallPercent()), "blue"),
                new StatCard("calendar", "Booking requests", String.valueOf(total), "navy"),
                new StatCard("check-circle", "Approval rate (decided)", rate, "teal"),
                new StatCard("tool", "Maintenance records", String.valueOf(r.maintenance().size()), "amber"));
        stats.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        stats.getChildren().forEach(n -> ((javafx.scene.layout.Region) n).setMaxWidth(Double.MAX_VALUE));

        // most used rooms
        CategoryAxis x1 = new CategoryAxis();
        NumberAxis y1 = new NumberAxis(0, 100, 20);
        y1.setLabel("Utilization %");
        y1.setMinorTickVisible(false);
        BarChart<String, Number> usage = chart(x1, y1);
        XYChart.Series<String, Number> s1 = new XYChart.Series<>();
        for (RoomUtil u : r.utilization().stream().limit(8).toList()) {
            s1.getData().add(new XYChart.Data<>(u.room().name(), Math.round(u.percent() * 10) / 10.0));
        }
        usage.getData().add(s1);

        // classroom vs lab
        CategoryAxis x2 = new CategoryAxis();
        NumberAxis y2 = new NumberAxis();
        y2.setLabel("Hours used");
        BarChart<String, Number> types = chart(x2, y2);
        XYChart.Series<String, Number> s2 = new XYChart.Series<>();
        for (RoomType t : RoomType.values()) {
            s2.getData().add(new XYChart.Data<>(t.label(), Math.round(r.minutesByType().getOrDefault(t, 0L) / 6.0) / 10.0));
        }
        types.getData().add(s2);

        // floors now
        CategoryAxis x3 = new CategoryAxis();
        NumberAxis y3 = new NumberAxis();
        y3.setLabel("Rooms");
        y3.setTickUnit(1);
        BarChart<String, Number> floors = chart(x3, y3);
        floors.setLegendVisible(true);
        XYChart.Series<String, Number> free = new XYChart.Series<>();
        free.setName("Free now");
        XYChart.Series<String, Number> all = new XYChart.Series<>();
        all.setName("Total rooms");
        r.floorNow().forEach((f, c) -> {
            free.getData().add(new XYChart.Data<>("Floor " + f, c[0]));
            all.getData().add(new XYChart.Data<>("Floor " + f, c[1]));
        });
        floors.getData().add(free);
        floors.getData().add(all);

        // approval statistics
        PieChart pie = new PieChart();
        for (BookingStatus st : BookingStatus.values()) {
            int n = r.bookingStats().get(st);
            if (n > 0) pie.getData().add(new PieChart.Data(st.label() + " (" + n + ")", n));
        }
        pie.setLegendSide(javafx.geometry.Side.BOTTOM);
        pie.setPrefHeight(300);
        pie.setAnimated(!Anim.reduced());
        if (pie.getData().isEmpty()) pie.setTitle("No bookings in this range");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            grid.getColumnConstraints().add(cc);
        }
        grid.add(Forms.card("Most used rooms", usage), 0, 0);
        grid.add(Forms.card("Classroom vs laboratory usage", types), 1, 0);
        grid.add(Forms.card("Available rooms by floor (right now)", floors), 0, 1);
        grid.add(Forms.card("Booking approval statistics", pie), 1, 1);

        List<MaintenanceRecord> maint = r.maintenance();
        var table = Tables.table(maint, "No maintenance records", "Nothing was reported in this period.",
                Tables.text("Reported", m -> Dt.dateTime(m.start()), 140),
                Tables.text("Room", MaintenanceRecord::roomName, 90),
                Tables.text("Problem", MaintenanceRecord::description, 280),
                Tables.text("Reported by", MaintenanceRecord::reporterName, 140),
                Tables.node("Status", m -> StatusBadge.of(m.status()), 120));

        content.getChildren().setAll(stats, grid, Forms.card("Maintenance records", table));
        Anim.stagger(content.getChildren());
    }

    private static BarChart<String, Number> chart(CategoryAxis x, NumberAxis y) {
        BarChart<String, Number> c = new BarChart<>(x, y);
        c.setLegendVisible(false);
        c.setAnimated(!Anim.reduced());
        c.setPrefHeight(300);
        c.setCategoryGap(14);
        return c;
    }
}
