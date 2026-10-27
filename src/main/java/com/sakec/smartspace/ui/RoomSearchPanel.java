package com.sakec.smartspace.ui;

import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.model.SearchCriteria;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Floor selector (seven floors) plus search filters. Calls onSearch whenever the user asks for new results. */
public class RoomSearchPanel extends VBox {
    private final ToggleGroup floors = new ToggleGroup();
    private final ToggleButton[] floorButtons = new ToggleButton[8]; // index 0 = all floors
    private final ComboBox<String> typeBox = new ComboBox<>();
    private final CheckBox nowCheck = new CheckBox("Right now");
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final ComboBox<LocalTime> startBox;
    private final ComboBox<LocalTime> endBox;
    private final TextField capacityField = new TextField();
    private final TextField equipmentField = new TextField();
    private final CheckBox availableOnly = new CheckBox("Available only");
    private final boolean includeInactive;
    private Runnable onSearch = () -> { };

    public RoomSearchPanel(boolean includeInactive) {
        super(14);
        this.includeInactive = includeInactive;
        getStyleClass().addAll("card", "search-panel");
        setPadding(new Insets(16));

        // floor selector
        FlowPane floorRow = new FlowPane(8, 8);
        for (int i = 0; i <= 7; i++) {
            ToggleButton b = new ToggleButton(i == 0 ? "All floors" : "Floor " + i);
            b.getStyleClass().add("floor-chip");
            b.setToggleGroup(floors);
            b.setUserData(i);
            floorButtons[i] = b;
            floorRow.getChildren().add(b);
        }
        floorButtons[0].setSelected(true);
        floors.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true); else onSearch.run();
        });

        // filters
        typeBox.getItems().add("Any type");
        for (RoomType t : RoomType.values()) typeBox.getItems().add(t.label());
        typeBox.setValue("Any type");
        nowCheck.setSelected(true);
        LocalTime start = Dt.roundUp(LocalTime.now().plusMinutes(15), 15);
        if (start.isBefore(LocalTime.of(7, 0)) || start.isAfter(LocalTime.of(20, 0))) start = LocalTime.of(9, 0);
        startBox = Forms.timeCombo(start);
        endBox = Forms.timeCombo(start.plusHours(1));
        capacityField.setPromptText("Min seats");
        capacityField.setPrefWidth(100);
        capacityField.textProperty().addListener((o, a, b) -> { if (!b.matches("\\d{0,4}")) capacityField.setText(a); });
        equipmentField.setPromptText("Equipment, e.g. Projector");
        equipmentField.setPrefWidth(190);
        datePicker.setPrefWidth(140);
        datePicker.setEditable(false);
        for (var n : new javafx.scene.Node[]{datePicker, startBox, endBox}) n.disableProperty().bind(nowCheck.selectedProperty());

        FlowPane filters = new FlowPane(14, 12);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.getChildren().addAll(
                Forms.field("Room type", typeBox), Forms.field("When", nowCheck),
                Forms.field("Date", datePicker), Forms.field("From", startBox), Forms.field("To", endBox),
                Forms.field("Capacity", capacityField), Forms.field("Equipment", equipmentField),
                Forms.field(" ", availableOnly));

        javafx.scene.control.Button search = Forms.withIcon(Forms.primary("Search"), "search", "#FFFFFF");
        javafx.scene.control.Button reset = Forms.secondary("Reset");
        search.setOnAction(e -> onSearch.run());
        reset.setOnAction(e -> reset());
        HBox actions = new HBox(10, search, reset);
        getChildren().addAll(floorRow, filters, actions);
    }

    public void setOnSearch(Runnable r) { onSearch = r; }

    public void reset() {
        floorButtons[0].setSelected(true);
        typeBox.setValue("Any type");
        nowCheck.setSelected(true);
        capacityField.clear();
        equipmentField.clear();
        availableOnly.setSelected(false);
        onSearch.run();
    }

    /** Shows "x free" next to each floor, using {available, total} per floor. */
    public void updateFloorCounts(Map<Integer, int[]> counts) {
        for (int f = 1; f <= 7; f++) {
            int[] c = counts.get(f);
            floorButtons[f].setText("Floor " + f + (c == null || c[1] == 0 ? "" : "  ·  " + c[0] + "/" + c[1] + " free"));
        }
    }

    /** Builds the criteria, or throws AppException with a message the user can act on. */
    public SearchCriteria criteria() {
        Integer floor = (Integer) floors.getSelectedToggle().getUserData();
        if (floor == 0) floor = null;
        RoomType type = null;
        int idx = typeBox.getItems().indexOf(typeBox.getValue());
        if (idx > 0) type = RoomType.values()[idx - 1];
        LocalDateTime from, to;
        if (nowCheck.isSelected()) {
            from = LocalDateTime.now().withNano(0);
            to = from.plusMinutes(1);
        } else {
            LocalDate d = datePicker.getValue();
            if (d == null) throw new AppException("Pick a date.");
            if (!endBox.getValue().isAfter(startBox.getValue())) throw new AppException("The end time must be after the start time.");
            from = d.atTime(startBox.getValue());
            to = d.atTime(endBox.getValue());
        }
        Integer cap = capacityField.getText().isBlank() ? null : Integer.valueOf(capacityField.getText());
        return new SearchCriteria(floor, type, from, to, cap, equipmentField.getText(), availableOnly.isSelected(), includeInactive);
    }
}
