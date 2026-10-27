package com.sakec.smartspace.ui;

import com.sakec.smartspace.util.Dt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Day or week timetable. Time runs down the page; each day is a column. Empty areas are tinted as free
 * periods, overlapping blocks share the column width and are drawn in red.
 */
public class TimetableGrid extends ScrollPane {
    public record Block(String title, String line2, String line3, LocalDateTime start, LocalDateTime end,
                        boolean booking, Object ref) { }

    private static final int FIRST_HOUR = 7, LAST_HOUR = 21;
    private static final double HOUR_H = 56;

    public TimetableGrid(List<LocalDate> days, Map<LocalDate, List<Block>> blocks, Consumer<Block> onClick) {
        getStyleClass().add("tt-scroll");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.AS_NEEDED);

        // time axis
        VBox axis = new VBox();
        axis.setMinWidth(52);
        axis.setPrefWidth(52);
        Region axisHead = new Region();
        axisHead.setMinHeight(44);
        axis.getChildren().add(axisHead);
        for (int h = FIRST_HOUR; h < LAST_HOUR; h++) {
            Label l = new Label(LocalTime.of(h, 0).format(Dt.TIME));
            l.getStyleClass().add("tt-hour");
            l.setMinHeight(HOUR_H);
            l.setMaxHeight(HOUR_H);
            l.setAlignment(Pos.TOP_LEFT);
            axis.getChildren().add(l);
        }

        HBox columns = new HBox(8);
        columns.getChildren().add(axis);
        for (LocalDate d : days) {
            Label head = new Label(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth());
            head.getStyleClass().add(d.equals(LocalDate.now()) ? "tt-day-today" : "tt-day");
            head.setMaxWidth(Double.MAX_VALUE);
            head.setAlignment(Pos.CENTER);
            head.setMinHeight(36);
            DayColumn col = new DayColumn();
            layoutBlocks(col, blocks.getOrDefault(d, List.of()), onClick);
            VBox v = new VBox(8, head, col);
            v.setMinWidth(days.size() > 1 ? 120 : 300);
            HBox.setHgrow(v, Priority.ALWAYS);
            v.setMaxWidth(Double.MAX_VALUE);
            columns.getChildren().add(v);
        }
        columns.setPadding(new javafx.geometry.Insets(12));
        setContent(columns);
    }

    /** Pane that positions its children by the "y", "h", "lane" and "lanes" properties. */
    private static final class DayColumn extends Pane {
        DayColumn() {
            getStyleClass().add("tt-day-col");
            double h = (LAST_HOUR - FIRST_HOUR) * HOUR_H;
            setMinHeight(h);
            setPrefHeight(h);
            for (int i = 0; i <= LAST_HOUR - FIRST_HOUR; i++) {
                Region line = new Region();
                line.getStyleClass().add("tt-line");
                line.setMouseTransparent(true);
                line.getProperties().put("y", i * HOUR_H);
                line.getProperties().put("h", 1.0);
                line.getProperties().put("full", true);
                getChildren().add(line);
            }
        }

        @Override protected void layoutChildren() {
            double w = getWidth();
            for (Node n : getChildren()) {
                var p = n.getProperties();
                double y = (Double) p.get("y"), h = (Double) p.get("h");
                if (p.containsKey("full")) {
                    n.resizeRelocate(0, y, w, h);
                } else {
                    int lane = (Integer) p.get("lane"), lanes = (Integer) p.get("lanes");
                    double lw = (w - 6) / lanes;
                    n.resizeRelocate(3 + lane * lw, y + 1, lw - 3, Math.max(20, h - 2));
                }
            }
        }
    }

    private static void layoutBlocks(DayColumn col, List<Block> blocks, Consumer<Block> onClick) {
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparing(Block::start));
        int i = 0;
        while (i < sorted.size()) {
            // find cluster of transitively overlapping blocks
            int j = i;
            LocalDateTime clusterEnd = sorted.get(i).end();
            while (j + 1 < sorted.size() && sorted.get(j + 1).start().isBefore(clusterEnd)) {
                j++;
                if (sorted.get(j).end().isAfter(clusterEnd)) clusterEnd = sorted.get(j).end();
            }
            List<Block> cluster = sorted.subList(i, j + 1);
            List<LocalDateTime> laneEnds = new ArrayList<>();
            int[] laneOf = new int[cluster.size()];
            for (int k = 0; k < cluster.size(); k++) {
                int lane = 0;
                while (lane < laneEnds.size() && laneEnds.get(lane).isAfter(cluster.get(k).start())) lane++;
                if (lane == laneEnds.size()) laneEnds.add(cluster.get(k).end()); else laneEnds.set(lane, cluster.get(k).end());
                laneOf[k] = lane;
            }
            boolean conflict = cluster.size() > 1;
            for (int k = 0; k < cluster.size(); k++) {
                col.getChildren().add(node(cluster.get(k), laneOf[k], laneEnds.size(), conflict, onClick));
            }
            i = j + 1;
        }
    }

    private static Node node(Block b, int lane, int lanes, boolean conflict, Consumer<Block> onClick) {
        Label t = new Label(b.title());
        t.getStyleClass().add("tt-block-title");
        VBox box = new VBox(1, t);
        if (b.line2() != null) box.getChildren().add(small(b.line2()));
        if (b.line3() != null) box.getChildren().add(small(b.line3()));
        box.getChildren().add(small(Dt.range(b.start(), b.end())));
        box.getStyleClass().add(conflict ? "tt-block-conflict" : b.booking() ? "tt-block-booking" : "tt-block");
        box.setOnMouseClicked(e -> onClick.accept(b));
        Anim.hoverLift(box);

        double startH = b.start().getHour() + b.start().getMinute() / 60.0 - FIRST_HOUR;
        double endH = b.end().getHour() + b.end().getMinute() / 60.0 - FIRST_HOUR;
        startH = Math.max(0, startH);
        endH = Math.min(LAST_HOUR - FIRST_HOUR, endH);
        box.getProperties().put("y", startH * HOUR_H);
        box.getProperties().put("h", Math.max(0.25, endH - startH) * HOUR_H);
        box.getProperties().put("lane", lane);
        box.getProperties().put("lanes", lanes);
        box.setMinSize(0, 0);
        return box;
    }

    private static Label small(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("tt-block-line");
        return l;
    }
}
