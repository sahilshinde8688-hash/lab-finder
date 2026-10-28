package com.sakec.smartspace.view;

import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.FloorMapRegion;
import com.sakec.smartspace.model.NavigationEdge;
import com.sakec.smartspace.model.NavigationGraph;
import com.sakec.smartspace.model.NavigationNode;
import com.sakec.smartspace.model.NavigationPath;
import com.sakec.smartspace.model.NavigationResult;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.service.AvailabilityService;
import com.sakec.smartspace.service.AvailabilityEstimationService;
import com.sakec.smartspace.service.FloorMapService;
import com.sakec.smartspace.service.NavigationService;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Icons;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Config;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.util.RoomLabelMatcher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Interactive, image-based floor map. Region coordinates are normalized against the supplied source images. */
public class FloorMapPage extends Page {
    private record Region(String number, String type, double x, double y, double width, double height) {
        String label() { return type.equalsIgnoreCase(number) ? type : type + " " + number; }
    }
    private record Floor(int number, String image, double width, double height, List<Region> regions) { }
    private record ViewState(double zoom, double translateX, double translateY) { }
    private record PageData(List<Room> rooms, List<FloorMapRegion> fourth, List<FloorMapRegion> fifth,
                            NavigationGraph navigationGraph) { }

    private static final List<Floor> FLOORS = List.of(
            new Floor(4, "/images/fourth_floor.png", 1427, 1076, List.of(
                    new Region("409", "Classroom", .085, .266, .119, .113),
                    new Region("420", "Laboratory", .085, .379, .119, .146),
                    new Region("422", "Laboratory", .085, .598, .119, .070),
                    new Region("401", "Classroom", .081, .670, .151, .150),
                    new Region("402", "Laboratory", .232, .670, .174, .150),
                    new Region("419", "Laboratory", .237, .266, .135, .113),
                    new Region("418", "Laboratory", .237, .379, .135, .115),
                    new Region("417", "Washroom", .237, .494, .072, .088),
                    new Region("421", "Seminar hall", .406, .266, .198, .196),
                    new Region("416", "Washroom", .309, .563, .064, .069),
                    new Region("415", "Laboratory", .373, .491, .174, .142),
                    new Region("403", "Room", .424, .633, .071, .035),
                    new Region("Library", "Library", .495, .491, .230, .329),
                    new Region("Library", "Library", .725, .491, .182, .329),
                    new Region("West", "Staircase", .121, .519, .084, .076),
                    new Region("West", "Lift", .238, .598, .039, .038),
                    new Region("Central", "Staircase", .405, .697, .076, .125),
                    new Region("East", "Lift", .603, .266, .058, .069),
                    new Region("514", "Store room", .688, .266, .034, .135),
                    new Region("515", "Store room", .722, .266, .037, .135),
                    new Region("510", "Office", .634, .399, .092, .063),
                    new Region("509", "Library stack room", .760, .266, .147, .226)
            )),
            new Floor(5, "/images/fifth_floor.png", 1487, 1056, List.of(
                    new Region("520", "Laboratory", .074, .264, .121, .116),
                    new Region("521", "Laboratory", .074, .380, .121, .124),
                    new Region("524A", "IT-HOD", .074, .504, .105, .041),
                    new Region("522", "Staff room", .074, .620, .121, .068),
                    new Region("501", "Laboratory", .073, .688, .149, .142),
                    new Region("519", "Classroom", .228, .264, .141, .116),
                    new Region("518", "Laboratory", .228, .380, .141, .124),
                    new Region("517", "Washroom", .228, .504, .075, .090),
                    new Region("516", "Washroom", .303, .580, .066, .054),
                    new Region("502A", "Staff room", .222, .688, .058, .142),
                    new Region("502", "Classroom", .280, .688, .156, .142),
                    new Region("523", "Laboratory", .403, .264, .097, .208),
                    new Region("524", "Laboratory", .500, .264, .103, .208),
                    new Region("511", "Department office", .632, .399, .096, .073),
                    new Region("515", "Classroom", .368, .505, .114, .143),
                    new Region("514", "Classroom", .482, .505, .124, .143),
                    new Region("513", "Classroom", .606, .505, .124, .143),
                    new Region("503", "Laboratory", .490, .688, .179, .142),
                    new Region("504", "Laboratory", .669, .688, .147, .142),
                    new Region("506", "Classroom", .767, .688, .157, .142),
                    new Region("509", "Classroom", .765, .264, .155, .144),
                    new Region("514", "Store room", .691, .264, .039, .144),
                    new Region("516", "Store room", .730, .264, .035, .144),
                    new Region("508", "PG classroom", .765, .408, .155, .096),
                    new Region("507", "Classroom", .765, .504, .155, .144),
                    new Region("West", "Staircase", .108, .536, .077, .080),
                    new Region("West", "Lift", .230, .594, .045, .044),
                    new Region("Central", "Staircase", .420, .703, .074, .126),
                    new Region("East", "Lift", .601, .264, .068, .076)
            ))
    );

    private final boolean coordinator;
    private final boolean canViewTimetable;
    private final Navigator navigator;
    private final javafx.scene.control.ComboBox<Integer> floorPicker = new javafx.scene.control.ComboBox<>();
    private final ComboBox<String> searchFloorFilter = new ComboBox<>();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final TextField capacityFilter = new TextField();
    private final TextField equipmentFilter = new TextField();
    private final CheckBox availableOnlyFilter = new CheckBox("Available only");
    private final DatePicker availabilityDate = new DatePicker(LocalDate.now());
    private final ComboBox<LocalTime> availabilityTime = Forms.timeCombo(Dt.roundUp(LocalTime.now(), 15));
    private final ComboBox<Integer> availabilityDuration = new ComboBox<>();
    private final Label availabilityStatus = new Label();
    private final ImageView mapImage = new ImageView();
    private final Pane regionLayer = new Pane();
    private final Pane navigationLayer = new Pane();
    private final Group mapGroup = new Group();
    private final StackPane mapViewport = new StackPane();
    private final ImageView overviewImage = new ImageView();
    private final Rectangle overviewWindow = new Rectangle();
    private final StackPane overview = new StackPane(overviewImage, overviewWindow);
    private final VBox details = new VBox(10);
    private final Label selectedTitle = new Label("Select a room on the map");
    private final Label selectedInfo = new Label("Select a mapped location to see verified room information.");
    private final Label searchStatus = new Label();
    private final TextField searchField = new TextField();
    private final ContextMenu searchSuggestions = new ContextMenu();
    private final Button roomDetailsButton = Forms.secondary("View room details");
    private final Button timetableButton = Forms.secondary("View timetable");
    private final Button routeButton = Forms.secondary("Find route");
    private final Label routeStatus = Forms.muted("Indoor routes are unavailable until a coordinator configures and verifies corridor connections and floor transitions.");
    private final Button reportButton = Forms.secondary("Report a problem");
    private final Button bookingButton = Forms.primary("Request booking");
    private final Button calibrationButton = Forms.secondary("Calibrate region");
    private final Button navigationManagementButton = Forms.secondary("Navigation management");
    private final Button estimateButton = Forms.secondary("Estimate future");
    private final List<Label> regionLabels = new ArrayList<>();
    private final List<Rectangle> regionShapes = new ArrayList<>();
    private final List<Tooltip> regionTooltips = new ArrayList<>();
    private final Map<Integer, ViewState> floorViews = new HashMap<>();
    private final Map<Integer, Image> floorImages = new HashMap<>();
    private final Map<Integer, Map<String, FloorMapRegion>> configuredRegions = new HashMap<>();
    private final Map<Region, String> regionKeys = new HashMap<>();
    private Map<Integer, RoomAvailability> availabilityByRoomId = Map.of();
    private NavigationGraph navigationGraph = NavigationGraph.empty();
    private NavigationGraph navigationPreviewGraph;
    private NavigationPath activeRoute;
    private List<Room> rooms = List.of();
    private Region selectedRegion;
    private int activeFloor;
    private int availabilityRequest;
    private int estimationRequest;
    private boolean availabilityLoading;
    private boolean availabilityRefreshQueued;
    private boolean availabilityStale = true;
    private LocalDateTime lastSuccessfulRefresh;
    private boolean pageVisible;
    private boolean pageDataLoading;
    private boolean pageDataRefreshQueued;
    private PageData currentPageData;
    private Timeline refreshPoller;
    private double zoom = 1;
    private double pressX;
    private double pressY;
    private double dragX;
    private double dragY;
    private boolean dragged;
    private boolean showBoundaries = true;
    private boolean showLabels = true;
    private boolean showAvailability = true;
    private boolean fitOnNextLayout = true;
    private boolean calibrationDrawMode;
    private boolean drawingRegion;
    private double drawStartX;
    private double drawStartY;
    private Rectangle drawingRectangle;

    public FloorMapPage(boolean coordinator) {
        this(coordinator, false, null);
    }

    public FloorMapPage(boolean coordinator, boolean canViewTimetable, Navigator navigator) {
        this.coordinator = coordinator;
        this.canViewTimetable = canViewTimetable;
        this.navigator = navigator;
        body.setSpacing(14);
        searchStatus.getStyleClass().add("muted");
        searchStatus.setManaged(false);
        searchStatus.setVisible(false);

        floorPicker.getItems().addAll(4, 5);
        floorPicker.setValue(4);
        floorPicker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer floor) { return floor == null ? "" : "Floor " + floor; }
            @Override public Integer fromString(String value) { return Integer.valueOf(value.replace("Floor ", "")); }
        });
        floorPicker.setOnAction(e -> showFloor());
        Button zoomIn = Forms.secondary("Zoom in");
        Button zoomOut = Forms.secondary("Zoom out");
        Button fit = Forms.secondary("Fit to screen");
        Button reset = Forms.secondary("Reset view");
        Button detailsToggle = Forms.ghost("Hide details");
        detailsToggle.setTooltip(new Tooltip("Show or hide the room details panel"));
        zoomIn.setTooltip(new Tooltip("Zoom in around the map centre"));
        zoomOut.setTooltip(new Tooltip("Zoom out around the map centre"));
        fit.setTooltip(new Tooltip("Fit the whole floor plan in the map window"));
        reset.setTooltip(new Tooltip("Reset the map to its fitted view"));
        zoomIn.setOnAction(e -> zoomAt(zoom * 1.2, mapViewport.getWidth() / 2, mapViewport.getHeight() / 2));
        zoomOut.setOnAction(e -> zoomAt(zoom / 1.2, mapViewport.getWidth() / 2, mapViewport.getHeight() / 2));
        fit.setOnAction(e -> fitMap());
        reset.setOnAction(e -> {
            mapGroup.setTranslateX(0);
            mapGroup.setTranslateY(0);
            fitMap();
            saveView();
        });

        searchFloorFilter.getItems().addAll("Any floor", "Floor 4", "Floor 5");
        searchFloorFilter.setValue("Any floor");
        searchFloorFilter.setPromptText("Search floor");
        searchFloorFilter.setPrefWidth(115);
        typeFilter.getItems().addAll("All types", "Classroom", "Computer lab", "Laboratory",
                "Seminar hall", "Library", "Office", "Other facilities");
        typeFilter.setValue("All types");
        typeFilter.setPrefWidth(155);
        capacityFilter.setPrefWidth(100);
        capacityFilter.setPromptText("Min seats");
        equipmentFilter.setPromptText("Equipment");
        equipmentFilter.setPrefWidth(150);
        availabilityDate.setEditable(false);
        availabilityDate.setPrefWidth(140);
        availabilityTime.setPrefWidth(100);
        availabilityDuration.getItems().setAll(15, 30, 60, 120);
        availabilityDuration.setValue(60);
        availabilityDuration.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer minutes) {
                if (minutes == null) return "";
                return minutes >= 60 && minutes % 60 == 0 ? minutes / 60 + " hr" : minutes + " min";
            }
            @Override public Integer fromString(String value) { return Integer.valueOf(value); }
        });
        availabilityDuration.setPrefWidth(85);

        searchField.setPromptText("Search rooms, facilities, or equipment");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((o, oldValue, value) -> updateSuggestions(value));
        searchField.setOnAction(e -> chooseFirstSuggestion());
        searchFloorFilter.valueProperty().addListener((o, a, b) -> updateSuggestions(searchField.getText()));
        typeFilter.valueProperty().addListener((o, a, b) -> updateSuggestions(searchField.getText()));
        capacityFilter.textProperty().addListener((o, a, b) -> updateSuggestions(searchField.getText()));
        equipmentFilter.textProperty().addListener((o, a, b) -> updateSuggestions(searchField.getText()));
        availableOnlyFilter.selectedProperty().addListener((o, a, b) -> updateSuggestions(searchField.getText()));

        CheckBox boundaries = new CheckBox("Boundaries");
        boundaries.setSelected(true);
        boundaries.selectedProperty().addListener((o, oldValue, value) -> {
            showBoundaries = value;
            updateRegionStyles();
        });
        CheckBox labels = new CheckBox("Labels");
        labels.setSelected(true);
        labels.selectedProperty().addListener((o, oldValue, value) -> {
            showLabels = value;
            updateRegionStyles();
        });
        CheckBox availability = new CheckBox("Availability");
        availability.setSelected(true);
        availability.selectedProperty().addListener((o, oldValue, value) -> {
            showAvailability = value;
            updateRegionStyles();
        });
        Button now = Forms.secondary("Available now");
        now.setTooltip(new Tooltip("Show availability for the current time"));
        now.setOnAction(e -> {
            availabilityDate.setValue(LocalDate.now());
            availabilityTime.setValue(LocalTime.now().withSecond(0).withNano(0));
        });
        Button refresh = Forms.secondary("Refresh");
        refresh.setTooltip(new Tooltip("Refresh room availability from the timetable, bookings and maintenance records"));
        refresh.setOnAction(e -> refreshAvailability());
        estimateButton.setTooltip(new Tooltip("Show prediction-only suggestions for a future time slot"));
        estimateButton.setOnAction(e -> estimateFutureAvailability());
        availabilityDate.valueProperty().addListener((o, a, b) -> refreshAvailability());
        availabilityTime.valueProperty().addListener((o, a, b) -> refreshAvailability());
        availabilityDuration.valueProperty().addListener((o, a, b) -> refreshAvailability());

        calibrationButton.setVisible(coordinator);
        calibrationButton.setManaged(coordinator);
        calibrationButton.setDisable(true);
        calibrationButton.setOnAction(e -> toggleCalibrationDrawMode());
        navigationManagementButton.setVisible(coordinator);
        navigationManagementButton.setManaged(coordinator);
        navigationManagementButton.setOnAction(e -> openNavigationManagement());
        HBox toolbar = new HBox(8, floorPicker, searchField, zoomOut, zoomIn, fit, reset, detailsToggle,
                calibrationButton, navigationManagementButton);
        toolbar.getStyleClass().add("map-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox timeControls = new HBox(8,
                Forms.field("Date", availabilityDate),
                Forms.field("Start time", availabilityTime),
                Forms.field("Check for", availabilityDuration),
                now, refresh, estimateButton);
        timeControls.setAlignment(Pos.BOTTOM_LEFT);
        timeControls.getStyleClass().add("map-time-controls");
        Button clearFilters = Forms.ghost("Clear filters");
        clearFilters.setOnAction(e -> {
            searchField.clear();
            searchFloorFilter.setValue("Any floor");
            typeFilter.setValue("All types");
            capacityFilter.clear();
            equipmentFilter.clear();
            availableOnlyFilter.setSelected(false);
            showStatus("");
        });
        HBox filters = new HBox(8, searchFloorFilter, typeFilter, capacityFilter,
                equipmentFilter, availableOnlyFilter, clearFilters);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("map-filters");
        availabilityStatus.getStyleClass().add("muted-small");
        availabilityStatus.setWrapText(true);
        availabilityStatus.getStyleClass().add("map-availability-status");
        HBox legend = new HBox(14, boundaries, labels, availability,
                legendItem("#15803D", "Available"),
                legendItem("#D04444", "Occupied"),
                legendItem("#D99A19", "Reserved"),
                legendItem("#8A94A6", "Maintenance / unavailable / unknown"));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getStyleClass().add("map-legend");
        mapViewport.setMinSize(320, 420);
        mapViewport.setPrefSize(900, 660);
        mapViewport.setStyle("-fx-background-color: #e8edf3; -fx-border-color: #d5dce5; -fx-border-width: 1;");
        mapViewport.setClip(new Rectangle());
        mapViewport.widthProperty().addListener((o, a, b) -> handleViewportResize());
        mapViewport.heightProperty().addListener((o, a, b) -> handleViewportResize());
        mapViewport.addEventFilter(MouseEvent.MOUSE_PRESSED, this::startPan);
        mapViewport.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::pan);
        mapViewport.addEventFilter(MouseEvent.MOUSE_RELEASED, this::finishRegionDrawing);
        mapViewport.addEventFilter(ScrollEvent.SCROLL, this::zoomOnScroll);
        mapViewport.setCursor(Cursor.OPEN_HAND);

        mapImage.setPreserveRatio(true);
        mapImage.setSmooth(true);
        navigationLayer.setMouseTransparent(true);
        mapGroup.getChildren().addAll(mapImage, regionLayer, navigationLayer);
        overview.setMaxSize(160, 122);
        overview.setMinSize(160, 122);
        overview.setStyle("-fx-background-color: rgba(255,255,255,.88); -fx-border-color: #77839a; -fx-border-width: 1;");
        overviewImage.setFitWidth(154);
        overviewImage.setFitHeight(116);
        overviewImage.setPreserveRatio(true);
        overviewWindow.setFill(Color.color(.05, .30, .88, .12));
        overviewWindow.setStroke(Color.web("#0b3fc5"));
        overviewWindow.setStrokeWidth(1.5);
        overview.setOnMouseClicked(e -> centerMapOnOverview(e.getX(), e.getY()));
        StackPane.setAlignment(overview, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(overview, new Insets(12));
        mapViewport.getChildren().addAll(mapGroup, overview);

        selectedTitle.getStyleClass().add("section-title");
        selectedInfo.setWrapText(true);
        selectedInfo.getStyleClass().add("muted");
        roomDetailsButton.setDisable(true);
        roomDetailsButton.setOnAction(e -> openRoomDetails());
        timetableButton.setDisable(true);
        timetableButton.setOnAction(e -> {
            if (navigator != null) navigator.go("timetable");
        });
        routeButton.setDisable(true);
        routeButton.setOnAction(e -> openRouteDialog());
        routeStatus.setWrapText(true);
        routeStatus.getStyleClass().add("muted-small");
        reportButton.setDisable(true);
        reportButton.setOnAction(e -> {
            Room room = selectedRoom();
            if (room != null) Dialogs.report(room, this::show);
        });
        bookingButton.setVisible(!coordinator);
        bookingButton.setManaged(!coordinator);
        bookingButton.setDisable(true);
        bookingButton.setOnAction(e -> {
            Room room = selectedRoom();
            if (room != null && !coordinator) Dialogs.book(room, this::show);
        });
        Label note = new Label("Availability uses timetable, approved bookings and maintenance data. It does not detect physical occupancy.");
        note.setWrapText(true);
        note.getStyleClass().add("muted");
        details.setPadding(new Insets(18));
        details.getStyleClass().add("card");
        details.getChildren().addAll(selectedTitle, selectedInfo, roomDetailsButton, timetableButton,
                bookingButton, routeButton, routeStatus, reportButton, note);
        details.setMinWidth(250);
        details.setPrefWidth(290);
        detailsToggle.setOnAction(e -> {
            boolean show = !details.isManaged();
            details.setManaged(show);
            details.setVisible(show);
            detailsToggle.setText(show ? "Hide details" : "Show details");
        });

        HBox workspace = new HBox(16, mapViewport, details);
        HBox.setHgrow(mapViewport, Priority.ALWAYS);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        body.getChildren().addAll(Forms.pageHeader("Interactive floor map", "Explore verified room labels on the fourth and fifth floors."),
                toolbar, timeControls, filters, legend, availabilityStatus, searchStatus, workspace);
        showFloor();
    }

    @Override public String title() { return "Floor map"; }

    @Override public void show() {
        pageVisible = true;
        if (refreshPoller == null) {
            int seconds = Math.max(10, Math.min(3600, Config.getInt("map.refreshSeconds", 30)));
            refreshPoller = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> loadPageData()));
            refreshPoller.setCycleCount(Timeline.INDEFINITE);
            refreshPoller.play();
        }
        loadPageData();
    }

    @Override public void hide() {
        pageVisible = false;
        availabilityRequest++;
        estimationRequest++;
        availabilityLoading = false;
        availabilityRefreshQueued = false;
        pageDataRefreshQueued = false;
        if (refreshPoller != null) refreshPoller.stop();
    }

    private void loadPageData() {
        if (!pageVisible) return;
        if (pageDataLoading) {
            pageDataRefreshQueued = true;
            return;
        }
        pageDataLoading = true;
        Async.run(() -> {
            RoomDao roomDao = new RoomDao();
            FloorMapService mapService = new FloorMapService();
            NavigationGraph publishedGraph = new NavigationService().published();
            return new PageData(roomDao.listActive(), mapService.regions(4, coordinator),
                    mapService.regions(5, coordinator), publishedGraph);
        }, loaded -> {
            pageDataLoading = false;
            if (!pageVisible) return;
            String selectedKey = selectedRegion == null ? null : regionKeys.get(selectedRegion);
            boolean regionsChanged = currentPageData == null
                    || !currentPageData.fourth().equals(loaded.fourth())
                    || !currentPageData.fifth().equals(loaded.fifth());
            boolean roomsChanged = currentPageData == null || !currentPageData.rooms().equals(loaded.rooms());
            boolean navigationChanged = currentPageData == null
                    || !currentPageData.navigationGraph().equals(loaded.navigationGraph());
            rooms = loaded.rooms();
            configuredRegions.put(4, indexRegions(loaded.fourth()));
            configuredRegions.put(5, indexRegions(loaded.fifth()));
            navigationGraph = loaded.navigationGraph();
            currentPageData = loaded;
            if (regionsChanged || activeFloor == 0) showFloor(selectedKey);
            else if (roomsChanged && selectedRegion != null) updateSelectionDetails(selectedRegion);
            if (navigationChanged) renderNavigationOverlay();
            updateRouteAvailability();
            refreshAvailability();
            if (pageDataRefreshQueued) {
                pageDataRefreshQueued = false;
                loadPageData();
            }
        }, error -> {
            pageDataLoading = false;
            if (!pageVisible) return;
            availabilityStale = true;
            availabilityByRoomId = Map.of();
            availabilityStatus.getStyleClass().remove("muted-small");
            if (!availabilityStatus.getStyleClass().contains("error-text")) {
                availabilityStatus.getStyleClass().add("error-text");
            }
            availabilityStatus.setText("STALE DATA — floor-map data could not be refreshed: "
                    + Errors.message(error) + lastRefreshText());
            updateRegionStyles();
            if (selectedRegion != null) updateSelectionDetails(selectedRegion);
            if (pageDataRefreshQueued) {
                pageDataRefreshQueued = false;
                loadPageData();
            }
        });
    }

    private void refreshAvailability() {
        if (!pageVisible) return;
        if (rooms.isEmpty()) return;
        if (availabilityLoading) {
            availabilityRefreshQueued = true;
            return;
        }
        if (availabilityDate.getValue() == null || availabilityTime.getValue() == null
                || availabilityDuration.getValue() == null) {
            availabilityStatus.setText("Choose a date, start time, and duration to check availability.");
            return;
        }
        LocalDateTime from = LocalDateTime.of(availabilityDate.getValue(), availabilityTime.getValue());
        LocalDateTime to = from.plusMinutes(availabilityDuration.getValue());
        int request = ++availabilityRequest;
        availabilityLoading = true;
        availabilityStale = true;
        availabilityStatus.getStyleClass().remove("error-text");
        availabilityStatus.getStyleClass().remove("muted-small");
        availabilityStatus.getStyleClass().add("muted-small");
        availabilityStatus.setText("Refreshing timetable, bookings, and maintenance…" + lastRefreshText());
        setRoomActionsEnabled(false);
        Async.run(() -> {
            AvailabilityService service = new AvailabilityService();
            AvailabilityService.Snapshot snapshot = service.snapshot(null, from, to);
            Map<Integer, RoomAvailability> result = new HashMap<>();
            for (Room room : rooms) {
                if (room.floor() == 4 || room.floor() == 5) {
                    result.put(room.id(), AvailabilityService.compute(room, from, to, snapshot));
                }
            }
            return Map.copyOf(result);
        }, result -> {
            if (!pageVisible || request != availabilityRequest) return;
            availabilityLoading = false;
            if (!currentAvailabilityWindowMatches(from, to)) {
                availabilityRefreshQueued = false;
                refreshAvailability();
                return;
            }
            availabilityByRoomId = result;
            availabilityStale = false;
            lastSuccessfulRefresh = LocalDateTime.now();
            availabilityStatus.getStyleClass().remove("error-text");
            availabilityStatus.setText("Timetable-based availability · " + Dt.range(from, to)
                    + " on " + from.format(Dt.DATE) + " · last successful refresh "
                    + lastSuccessfulRefresh.format(Dt.DATE) + " " + lastSuccessfulRefresh.format(Dt.TIME) + ".");
            updateRegionStyles();
            if (selectedRegion != null) updateSelectionDetails(selectedRegion);
            updateSuggestions(searchField.getText());
            runQueuedAvailabilityRefresh();
        }, error -> {
            if (!pageVisible || request != availabilityRequest) return;
            availabilityLoading = false;
            availabilityStale = true;
            availabilityByRoomId = Map.of();
            availabilityStatus.getStyleClass().remove("muted-small");
            if (!availabilityStatus.getStyleClass().contains("error-text")) availabilityStatus.getStyleClass().add("error-text");
            availabilityStatus.setText("STALE DATA — availability could not be refreshed: " + Errors.message(error)
                    + lastRefreshText() + " Use Refresh to try again.");
            updateRegionStyles();
            if (selectedRegion != null) updateSelectionDetails(selectedRegion);
            updateSuggestions(searchField.getText());
            runQueuedAvailabilityRefresh();
        });
    }

    private void estimateFutureAvailability() {
        if (availabilityDate.getValue() == null || availabilityTime.getValue() == null
                || availabilityDuration.getValue() == null) {
            Toast.error("Choose a date, start time, and duration first.");
            return;
        }
        LocalDateTime from = LocalDateTime.of(availabilityDate.getValue(), availabilityTime.getValue());
        LocalDateTime to = from.plusMinutes(availabilityDuration.getValue());
        Integer minimumCapacity = null;
        if (!capacityFilter.getText().isBlank()) {
            try {
                minimumCapacity = Integer.parseInt(capacityFilter.getText().trim());
                if (minimumCapacity < 0) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                Toast.error("Minimum capacity must be a non-negative whole number.");
                return;
            }
        }
        RoomType selectedType = switch (typeFilter.getValue() == null ? "" : typeFilter.getValue()) {
            case "Classroom" -> RoomType.CLASSROOM;
            case "Computer lab" -> RoomType.COMPUTER_LAB;
            case "Laboratory" -> RoomType.LABORATORY;
            case "Seminar hall" -> RoomType.SEMINAR_ROOM;
            default -> null;
        };
        Integer capacity = minimumCapacity;
        String equipment = equipmentFilter.getText();
        int request = ++estimationRequest;
        estimateButton.setDisable(true);
        Async.run(() -> new AvailabilityEstimationService().estimate(from, to, selectedType,
                capacity, equipment), recommendations -> {
            if (!pageVisible || request != estimationRequest) return;
            estimateButton.setDisable(false);
            if (recommendations.isEmpty()) {
                Toast.info("No rooms are currently clear for this slot. Predictions never override schedule conflicts or maintenance.");
                return;
            }
            StringBuilder summary = new StringBuilder("PREDICTION ONLY · Confirmed timetable, booking, and maintenance conflicts are excluded.\n\n");
            for (AvailabilityEstimationService.Recommendation recommendation : recommendations.stream().limit(8).toList()) {
                Room room = recommendation.availability().room();
                summary.append(room.name()).append(" · Floor ").append(room.floor())
                        .append(" · capacity ").append(room.capacity()).append('\n')
                        .append(String.format(Locale.ROOT, "Estimated free likelihood: %.0f%%",
                                recommendation.estimatedFreeLikelihood() * 100))
                        .append('\n').append(recommendation.explanation()).append("\n\n");
            }
            TextArea details = new TextArea(summary.toString());
            details.setEditable(false);
            details.setWrapText(true);
            details.setPrefRowCount(Math.min(18, recommendations.size() * 4 + 2));
            Modal modal = new Modal("Estimated room availability", Forms.field(
                    "Historical pattern for " + from.format(Dt.DATE) + " · " + Dt.range(from, to), details)).width(580);
            Button close = Forms.ghost("Close");
            close.setOnAction(event -> modal.close());
            modal.actions(close).show();
        }, error -> {
            if (!pageVisible || request != estimationRequest) return;
            estimateButton.setDisable(false);
            Toast.error(Errors.message(error));
        });
    }

    private boolean currentAvailabilityWindowMatches(LocalDateTime from, LocalDateTime to) {
        if (availabilityDate.getValue() == null || availabilityTime.getValue() == null
                || availabilityDuration.getValue() == null) return false;
        LocalDateTime currentFrom = LocalDateTime.of(availabilityDate.getValue(), availabilityTime.getValue());
        return currentFrom.equals(from) && currentFrom.plusMinutes(availabilityDuration.getValue()).equals(to);
    }

    private String lastRefreshText() {
        return lastSuccessfulRefresh == null ? " No successful refresh this session."
                : " Last successful refresh: " + lastSuccessfulRefresh.format(Dt.DATE)
                        + " " + lastSuccessfulRefresh.format(Dt.TIME) + ".";
    }

    private void runQueuedAvailabilityRefresh() {
        if (availabilityRefreshQueued && pageVisible) {
            availabilityRefreshQueued = false;
            refreshAvailability();
        }
    }

    private void showFloor() {
        showFloor(null);
    }

    private void showFloor(String preserveRegionKey) {
        Floor floor = FLOORS.stream().filter(f -> f.number() == floorPicker.getValue()).findFirst().orElseThrow();
        if (activeFloor != 0) saveView();
        Image image = floorImages.computeIfAbsent(floor.number(), ignored -> loadFloorImage(floor));
        if (image == null) return;
        activeFloor = floor.number();
        mapImage.setImage(image);
        mapImage.setFitWidth(floor.width());
        mapImage.setFitHeight(floor.height());
        regionLayer.setPrefSize(floor.width(), floor.height());
        regionLayer.setMinSize(floor.width(), floor.height());
        regionLayer.setMaxSize(floor.width(), floor.height());
        navigationLayer.setPrefSize(floor.width(), floor.height());
        navigationLayer.setMinSize(floor.width(), floor.height());
        navigationLayer.setMaxSize(floor.width(), floor.height());
        regionLayer.getChildren().clear();
        regionShapes.clear();
        regionLabels.clear();
        regionTooltips.clear();
        regionKeys.clear();
        Map<String, FloorMapRegion> saved = configuredRegions.getOrDefault(floor.number(), Map.of());
        for (int i = 0; i < floor.regions().size(); i++) {
            Region source = floor.regions().get(i);
            String key = regionKey(floor, i);
            FloorMapRegion config = saved.get(key);
            Region displayed = config == null ? source : new Region(config.number(), config.type(),
                    config.x(), config.y(), config.width(), config.height());
            regionKeys.put(displayed, key);
            addRegion(displayed, floor);
        }
        selectedRegion = null;
        clearSelection("Select a location on Floor " + floor.number());
        overviewImage.setImage(image);
        ViewState state = floorViews.get(floor.number());
        if (state == null) {
            fitOnNextLayout = true;
            fitMap();
        } else {
            fitOnNextLayout = false;
            zoom = state.zoom();
            mapGroup.setScaleX(zoom);
            mapGroup.setScaleY(zoom);
            mapGroup.setTranslateX(state.translateX());
            mapGroup.setTranslateY(state.translateY());
        }
        updateRegionStyles();
        renderNavigationOverlay();
        updateOverviewWindow();
        if (preserveRegionKey != null) {
            regionKeys.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(preserveRegionKey))
                    .map(Map.Entry::getKey).findFirst().ifPresent(this::select);
        }
    }

    private Image loadFloorImage(Floor floor) {
        try (var resource = FloorMapPage.class.getResourceAsStream(floor.image())) {
            if (resource == null) {
                Toast.error("The Floor " + floor.number() + " map image is missing from application resources.");
                return null;
            }
            Image image = new Image(resource);
            if (image.isError()) {
                Toast.error("The Floor " + floor.number() + " map image could not be loaded.");
                return null;
            }
            return image;
        } catch (java.io.IOException error) {
            Toast.error("The Floor " + floor.number() + " map image could not be read.");
            return null;
        }
    }

    private void addRegion(Region region, Floor floor) {
        Rectangle shape = new Rectangle(region.x() * floor.width(), region.y() * floor.height(),
                region.width() * floor.width(), region.height() * floor.height());
        shape.setUserData(region);
        shape.setArcWidth(3);
        shape.setArcHeight(3);
        shape.setFill(Color.color(.12, .34, .88, .04));
        shape.setStroke(Color.color(.08, .22, .72, .56));
        shape.setStrokeWidth(2.2);
        shape.setFocusTraversable(true);
        shape.setOnMouseEntered(e -> {
            if (selectedRegion != region) {
                shape.setFill(Color.color(.12, .34, .88, .22));
                shape.setStroke(Color.web("#0b3fc5"));
            }
        });
        shape.setOnMouseExited(e -> {
            updateRegionStyles();
        });
        shape.setOnMouseClicked(e -> { if (!dragged) select(region); e.consume(); });
        shape.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                select(region);
                e.consume();
            }
        });
        shape.setAccessibleText(region.label() + " · Floor " + floor.number());
        Tooltip tooltip = new Tooltip(region.label() + " · Floor " + floor.number() + " · Availability unknown");
        Tooltip.install(shape, tooltip);
        regionLayer.getChildren().add(shape);
        regionShapes.add(shape);
        regionTooltips.add(tooltip);

        Label label = new Label(region.number());
        label.setMouseTransparent(true);
        label.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #142b59; -fx-background-color: rgba(255,255,255,.72); -fx-padding: 1 3 1 3;");
        label.setLayoutX(shape.getX() + 4);
        label.setLayoutY(shape.getY() + 3);
        label.setManaged(false);
        regionLayer.getChildren().add(label);
        regionLabels.add(label);
    }

    private void select(Region region) {
        selectedRegion = region;
        calibrationButton.setDisable(!coordinator);
        updateRegionStyles();
        selectedTitle.setText(region.label() + " · Floor " + activeFloor);
        updateSelectionDetails(region);
    }

    private void updateSelectionDetails(Region region) {
        FloorMapRegion mapping = currentConfiguration(region);
        List<Room> matches = mapping == null ? matchingRooms(region) : List.of();
        Room match = configuredRoom(region);
        if (mapping == null && matches.size() == 1) match = matches.get(0);
        if (match == null) {
            String message = matches.size() > 1
                    ? "Configuration warning: this label matches more than one room record. Availability and actions are disabled until the mapping is corrected."
                    : "Configuration warning: this map label has no compatible room record. Availability is unknown; this location is not assumed to be available.";
            selectedInfo.setText(message);
            setRoomActionsEnabled(false);
            return;
        }
        RoomAvailability availability = availabilityByRoomId.get(match.id());
        if (availabilityLoading) {
            selectedInfo.setText(roomSummary(match) + "\nAvailability: Checking timetable, bookings, and maintenance…");
            setRoomActionsEnabled(false);
            return;
        }
        if (availability == null) {
            selectedInfo.setText(roomSummary(match) + "\nAvailability: Unknown. Refresh availability to try again.");
            setRoomActionsEnabled(false);
            return;
        }
        selectedInfo.setText(roomSummary(match) + "\nAvailability: " + availability.status().label()
                + " · " + availability.detail() + "\nFor: " + selectedAvailabilityWindow()
                + "\nBased on schedule data, not physical occupancy.");
        roomDetailsButton.setUserData(availability);
        setRoomActionsEnabled(true);
    }

    private String roomSummary(Room room) {
        return room.name() + " · " + room.type().label() + " · Floor " + room.floor()
                + "\nRoom ID: " + room.id() + " · Capacity: " + room.capacity()
                + " · Equipment: " + (room.facilityList().isEmpty()
                ? "Not recorded" : String.join(", ", room.facilityList()));
    }

    private String selectedAvailabilityWindow() {
        if (availabilityDate.getValue() == null || availabilityTime.getValue() == null
                || availabilityDuration.getValue() == null) return "not selected";
        LocalDateTime from = LocalDateTime.of(availabilityDate.getValue(), availabilityTime.getValue());
        return from.format(Dt.DATE) + " " + Dt.range(from, from.plusMinutes(availabilityDuration.getValue()));
    }

    private Room selectedRoom() {
        if (selectedRegion == null) return null;
        Room configured = configuredRoom(selectedRegion);
        if (configured != null) return configured;
        List<Room> matches = matchingRooms(selectedRegion);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private Room configuredRoom(Region region) {
        FloorMapRegion config = currentConfiguration(region);
        if (config == null || config.roomId() == null) return null;
        return rooms.stream().filter(room -> room.id() == config.roomId() && room.floor() == activeFloor)
                .findFirst().orElse(null);
    }

    private FloorMapRegion currentConfiguration(Region region) {
        String key = regionKeys.get(region);
        return configuredRegions.getOrDefault(activeFloor, Map.of()).get(key);
    }

    private List<Room> matchingRooms(Region region) {
        return RoomLabelMatcher.matches(rooms, activeFloor, region.number(), region.type());
    }

    private void setRoomActionsEnabled(boolean enabled) {
        boolean safe = enabled && !availabilityLoading && !availabilityStale;
        roomDetailsButton.setDisable(!safe);
        timetableButton.setDisable(!safe || !canViewTimetable || navigator == null);
        reportButton.setDisable(!safe);
        bookingButton.setDisable(!safe || coordinator);
        updateRouteAvailability(enabled);
    }

    private void updateRouteAvailability() {
        updateRouteAvailability(selectedRoom() != null);
    }

    private void updateRouteAvailability(boolean roomEnabled) {
        Room start = roomEnabled ? selectedRoom() : null;
        if (start == null) {
            routeButton.setDisable(true);
            routeStatus.setText(navigationGraph.published()
                    ? "Select a room with a valid map record to check for verified routes."
                    : "Indoor routes are unavailable until a coordinator configures and verifies corridor connections and floor transitions.");
            routeButton.setTooltip(new Tooltip(routeStatus.getText()));
            return;
        }
        if (!navigationGraph.published()) {
            String diagnosis = navigationGraph.nodes().stream().noneMatch(n -> n.floor() == start.floor())
                    ? "No navigation nodes configured for Floor " + start.floor() + ". "
                    : "";
            routeStatus.setText(diagnosis + "Indoor routes are unavailable until a coordinator configures and verifies corridor connections and floor transitions.");
            routeButton.setDisable(true);
            routeButton.setTooltip(new Tooltip(routeStatus.getText()));
            return;
        }
        var validation = NavigationService.validate(navigationGraph, rooms);
        if (!validation.valid()) {
            routeStatus.setText("Navigation graph failed validation: " + validation.issues().get(0).message()
                    + " Ask an authorized coordinator to resolve the diagnostics.");
            routeButton.setDisable(true);
            routeButton.setTooltip(new Tooltip(routeStatus.getText()));
            return;
        }
        boolean available = rooms.stream().filter(room -> room.active() && room.id() != start.id()
                        && (room.floor() == 4 || room.floor() == 5))
                .anyMatch(room -> NavigationService.calculateRoute(navigationGraph, rooms, start.id(), room.id(), false).found());
        if (!available) {
            NavigationResult result = rooms.stream().filter(room -> room.active() && room.id() != start.id()
                            && (room.floor() == 4 || room.floor() == 5))
                    .map(room -> NavigationService.calculateRoute(navigationGraph, rooms, start.id(), room.id(), false))
                    .findFirst().orElse(new NavigationResult(null, "No destination rooms are available."));
            routeStatus.setText(result.diagnostic() == null ? "Destination is unreachable." : result.diagnostic());
            routeButton.setDisable(true);
            routeButton.setTooltip(new Tooltip(routeStatus.getText()));
            return;
        }
        routeStatus.setText("Verified indoor routes are available from " + start.name() + ".");
        routeButton.setDisable(false);
        routeButton.setTooltip(new Tooltip("Choose a destination for " + start.name()));
    }

    private void openNavigationManagement() {
        if (!coordinator) {
            Toast.error("Only an authorized coordinator can manage indoor navigation.");
            return;
        }
        try {
            new NavigationManagementDialog(new NavigationService(), rooms, this::onNavigationGraphChanged).show();
        } catch (RuntimeException error) {
            Toast.error(Errors.message(error));
        }
    }

    private void onNavigationGraphChanged(NavigationGraph graph) {
        if (graph.published()) {
            navigationGraph = graph;
            navigationPreviewGraph = null;
            updateRouteAvailability();
        } else if (coordinator) {
            navigationPreviewGraph = graph;
        }
        renderNavigationOverlay();
    }

    private void openRouteDialog() {
        Room selectedStart = selectedRoom();
        if (selectedStart == null || routeButton.isDisabled()) {
            Toast.info(routeStatus.getText());
            return;
        }
        ComboBox<Room> start = new ComboBox<>();
        ComboBox<Room> destination = new ComboBox<>();
        List<Room> routeRooms = rooms.stream().filter(room -> room.active()
                && (room.floor() == 4 || room.floor() == 5))
                .sorted(java.util.Comparator.comparingInt(Room::floor).thenComparing(Room::name)).toList();
        start.getItems().setAll(routeRooms);
        start.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Room room) { return room == null ? "" : room.toString(); }
            @Override public Room fromString(String value) { return null; }
        });
        destination.setConverter(start.getConverter());
        start.setValue(selectedStart);
        CheckBox accessible = new CheckBox("Accessible route only");
        Label status = Forms.muted("");
        TextArea directions = new TextArea();
        directions.setEditable(false);
        directions.setWrapText(true);
        directions.setPrefRowCount(8);
        Button calculate = Forms.primary("Calculate route");
        Button clear = Forms.secondary("Clear highlighted route");
        Runnable refreshChoices = () -> {
            Room from = start.getValue();
            List<Room> reachable = from == null ? List.of() : routeRooms.stream()
                    .filter(room -> room.id() != from.id())
                    .filter(room -> NavigationService.calculateRoute(navigationGraph, rooms,
                            from.id(), room.id(), false).found()).toList();
            Room previous = destination.getValue();
            destination.getItems().setAll(reachable);
            destination.setValue(reachable.stream().filter(room -> previous != null
                    && room.id() == previous.id()).findFirst().orElse(reachable.isEmpty() ? null : reachable.get(0)));
            updateRouteCalculationState(start, destination, accessible, calculate, status);
        };
        start.valueProperty().addListener((o, old, value) -> refreshChoices.run());
        destination.valueProperty().addListener((o, old, value) ->
                updateRouteCalculationState(start, destination, accessible, calculate, status));
        accessible.selectedProperty().addListener((o, old, value) ->
                updateRouteCalculationState(start, destination, accessible, calculate, status));
        refreshChoices.run();
        calculate.setOnAction(e -> {
            Room from = start.getValue();
            Room to = destination.getValue();
            if (from == null || to == null) return;
            NavigationResult result = NavigationService.calculateRoute(navigationGraph, rooms,
                    from.id(), to.id(), accessible.isSelected());
            if (!result.found()) {
                status.setText(result.diagnostic());
                directions.clear();
                return;
            }
            activeRoute = result.path();
            floorPicker.setValue(from.floor());
            renderNavigationOverlay();
            status.setText("Route calculated using only verified navigation connections.");
            directions.setText(routeDirections(from, to, result.path()));
        });
        clear.setOnAction(e -> {
            activeRoute = null;
            directions.clear();
            renderNavigationOverlay();
            status.setText("Highlighted route cleared.");
        });
        VBox content = new VBox(10, Forms.field("Starting room", start),
                Forms.field("Destination room", destination), accessible, status,
                Forms.field("Step-by-step directions", directions));
        Modal modal = new Modal("Find an indoor route", content).width(600);
        Button close = Forms.ghost("Close");
        close.setOnAction(e -> modal.close());
        modal.actions(clear, close, calculate).show();
    }

    private void updateRouteCalculationState(ComboBox<Room> start, ComboBox<Room> destination,
                                             CheckBox accessible, Button calculate, Label status) {
        Room from = start.getValue();
        Room to = destination.getValue();
        if (from == null || to == null) {
            calculate.setDisable(true);
            status.setText("No reachable destination is configured for this starting room.");
            return;
        }
        NavigationResult result = NavigationService.calculateRoute(navigationGraph, rooms,
                from.id(), to.id(), accessible.isSelected());
        calculate.setDisable(!result.found());
        status.setText(result.found() ? "Verified route available." : result.diagnostic());
    }

    private String routeDirections(Room from, Room to, NavigationPath path) {
        StringBuilder text = new StringBuilder("Start in ").append(from.name()).append(" (Floor ")
                .append(from.floor()).append("). Use its configured corridor connection at ")
                .append(path.nodes().get(0).label()).append(".\n");
        for (int i = 0; i < path.edges().size(); i++) {
            NavigationEdge edge = path.edges().get(i);
            NavigationNode current = path.nodes().get(i);
            NavigationNode next = path.nodes().get(i + 1);
            text.append(i + 1).append(". Follow the verified ").append(edge.type().label().toLowerCase())
                    .append(" connection from ").append(current.label()).append(" to ")
                    .append(next.label()).append(" (Floor ").append(next.floor()).append(").\n");
        }
        text.append("Arrive at ").append(to.name()).append(" (Floor ").append(to.floor()).append(").");
        if (path.distanceMeters() != null) text.append("\nVerified route distance: ")
                .append(String.format(Locale.ROOT, "%.1f m", path.distanceMeters()));
        return text.toString();
    }

    private void renderNavigationOverlay() {
        if (activeFloor == 0) return;
        Floor currentFloor = FLOORS.stream().filter(f -> f.number() == activeFloor).findFirst().orElseThrow();
        navigationLayer.setPrefSize(currentFloor.width(), currentFloor.height());
        navigationLayer.setMinSize(currentFloor.width(), currentFloor.height());
        navigationLayer.setMaxSize(currentFloor.width(), currentFloor.height());
        navigationLayer.getChildren().clear();
        if (coordinator && navigationPreviewGraph != null) {
            for (NavigationEdge edge : navigationPreviewGraph.edges()) {
                NavigationNode from = navigationPreviewGraph.nodes().stream()
                        .filter(node -> node.id().equals(edge.fromNodeId())).findFirst().orElse(null);
                NavigationNode to = navigationPreviewGraph.nodes().stream()
                        .filter(node -> node.id().equals(edge.toNodeId())).findFirst().orElse(null);
                if (from == null || to == null || from.floor() != activeFloor || to.floor() != activeFloor) continue;
                Line line = new Line(from.x() * currentFloor.width(), from.y() * currentFloor.height(),
                        to.x() * currentFloor.width(), to.y() * currentFloor.height());
                line.setStroke(edge.verified() ? Color.web("#087443") : Color.web("#d18a00"));
                line.setStrokeWidth(5);
                navigationLayer.getChildren().add(line);
            }
            for (NavigationNode node : navigationPreviewGraph.nodes()) {
                if (node.floor() != activeFloor) continue;
                Circle marker = new Circle(node.x() * currentFloor.width(), node.y() * currentFloor.height(), 9,
                        node.verified() ? Color.web("#087443") : Color.web("#d18a00"));
                marker.setStroke(Color.WHITE);
                marker.setStrokeWidth(3);
                navigationLayer.getChildren().add(marker);
            }
        }
        if (activeRoute == null) return;
        for (int i = 0; i < activeRoute.edges().size(); i++) {
            NavigationNode from = activeRoute.nodes().get(i);
            NavigationNode to = activeRoute.nodes().get(i + 1);
            if (from.floor() != activeFloor || to.floor() != activeFloor) continue;
            Line line = new Line(from.x() * currentFloor.width(), from.y() * currentFloor.height(),
                    to.x() * currentFloor.width(), to.y() * currentFloor.height());
            line.setStroke(Color.web("#155eef"));
            line.setStrokeWidth(8);
            line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            navigationLayer.getChildren().add(line);
        }
        for (NavigationNode node : activeRoute.nodes()) {
            if (node.floor() != activeFloor) continue;
            Circle marker = new Circle(node.x() * currentFloor.width(), node.y() * currentFloor.height(), 11,
                    Color.web("#155eef"));
            marker.setStroke(Color.WHITE);
            marker.setStrokeWidth(4);
            navigationLayer.getChildren().add(marker);
        }
    }

    private void openRoomDetails() {
        Object value = roomDetailsButton.getUserData();
        if (value instanceof RoomAvailability availability) {
            Dialogs.roomDetails(availability, coordinator ? null : () -> Dialogs.book(availability.room(), this::show));
        }
    }

    private void startPan(MouseEvent event) {
        if (calibrationDrawMode && coordinator && selectedRegion != null) {
            javafx.geometry.Point2D point = mapGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
            drawingRegion = true;
            dragged = true;
            drawStartX = point.getX();
            drawStartY = point.getY();
            drawingRectangle = new Rectangle(drawStartX, drawStartY, 0, 0);
            drawingRectangle.setFill(Color.color(.95, .55, .08, .24));
            drawingRectangle.setStroke(Color.web("#b54708"));
            drawingRectangle.getStrokeDashArray().setAll(7.0, 4.0);
            regionLayer.getChildren().add(drawingRectangle);
            event.consume();
            return;
        }
        pressX = event.getSceneX();
        pressY = event.getSceneY();
        dragX = mapGroup.getTranslateX();
        dragY = mapGroup.getTranslateY();
        dragged = false;
        mapViewport.setCursor(Cursor.CLOSED_HAND);
    }

    private void pan(MouseEvent event) {
        if (drawingRegion && drawingRectangle != null) {
            javafx.geometry.Point2D point = mapGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
            drawingRectangle.setX(Math.min(drawStartX, point.getX()));
            drawingRectangle.setY(Math.min(drawStartY, point.getY()));
            drawingRectangle.setWidth(Math.abs(point.getX() - drawStartX));
            drawingRectangle.setHeight(Math.abs(point.getY() - drawStartY));
            event.consume();
            return;
        }
        double dx = event.getSceneX() - pressX;
        double dy = event.getSceneY() - pressY;
        if (Math.abs(dx) + Math.abs(dy) > 4) dragged = true;
        mapGroup.setTranslateX(dragX + dx);
        mapGroup.setTranslateY(dragY + dy);
        updateOverviewWindow();
        saveView();
    }

    private void finishRegionDrawing(MouseEvent event) {
        if (drawingRegion && drawingRectangle != null) {
            Floor floor = FLOORS.stream().filter(candidate -> candidate.number() == activeFloor).findFirst().orElseThrow();
            double x = drawingRectangle.getX() / floor.width();
            double y = drawingRectangle.getY() / floor.height();
            double width = drawingRectangle.getWidth() / floor.width();
            double height = drawingRectangle.getHeight() / floor.height();
            regionLayer.getChildren().remove(drawingRectangle);
            drawingRectangle = null;
            drawingRegion = false;
            calibrationDrawMode = false;
            mapViewport.setCursor(Cursor.OPEN_HAND);
            if (width > .002 && height > .002) openCalibrationDialog(selectedRegion, x, y, width, height);
            else showStatus("The drawn boundary was too small. Draw a larger rectangular region.");
            event.consume();
            return;
        }
        if (!calibrationDrawMode) mapViewport.setCursor(Cursor.OPEN_HAND);
    }

    private void toggleCalibrationDrawMode() {
        if (!coordinator || selectedRegion == null) return;
        calibrationDrawMode = !calibrationDrawMode;
        mapViewport.setCursor(calibrationDrawMode ? Cursor.CROSSHAIR : Cursor.OPEN_HAND);
        showStatus(calibrationDrawMode
                ? "Drag a rectangle around the selected map location. Coordinates are normalized to the original floor image."
                : "");
    }

    private void openCalibrationDialog(Region target, double x, double y, double width, double height) {
        String key = regionKeys.get(target);
        FloorMapRegion existing = configuredRegions.getOrDefault(activeFloor, Map.of()).get(key);
        Room currentRoom = configuredRoom(target);
        if (existing == null && currentRoom == null) {
            List<Room> candidates = matchingRooms(target);
            if (candidates.size() == 1) currentRoom = candidates.get(0);
        }
        TextField number = new TextField(target.number());
        TextField type = new TextField(target.type());
        TextField xField = new TextField(decimal(x));
        TextField yField = new TextField(decimal(y));
        TextField widthField = new TextField(decimal(width));
        TextField heightField = new TextField(decimal(height));
        ComboBox<Room> roomBox = new ComboBox<>();
        roomBox.getItems().setAll(rooms.stream().filter(room -> room.floor() == activeFloor).toList());
        roomBox.setPromptText("No room association");
        roomBox.setMaxWidth(Double.MAX_VALUE);
        roomBox.setValue(currentRoom);
        Label error = Forms.errorLabel();
        Label status = Forms.muted("Changes are saved as a draft until published. Students continue to see the published geometry.");
        VBox form = new VBox(12,
                Forms.field("Map number or label", number),
                Forms.field("Location type", type),
                new HBox(10, Forms.field("X (0.0–1.0)", xField), Forms.field("Y (0.0–1.0)", yField)),
                new HBox(10, Forms.field("Width", widthField), Forms.field("Height", heightField)),
                Forms.field("Configured room (optional)", roomBox),
                status, error);
        Modal modal = new Modal("Calibrate Floor " + activeFloor + " map region", form).width(520);
        Button cancel = Forms.secondary("Cancel");
        Button draft = Forms.secondary("Save draft");
        Button publish = Forms.primary("Validate and publish");
        cancel.setOnAction(e -> modal.close());
        draft.setOnAction(e -> saveCalibration(modal, error, key, number, type, xField, yField,
                widthField, heightField, roomBox, false));
        publish.setOnAction(e -> saveCalibration(modal, error, key, number, type, xField, yField,
                widthField, heightField, roomBox, true));
        modal.actions(cancel, draft, publish).show();
    }

    private void saveCalibration(Modal modal, Label error, String key, TextField number,
                                TextField type, TextField x, TextField y, TextField width, TextField height,
                                ComboBox<Room> roomBox, boolean publish) {
        Forms.showError(error, null);
        FloorMapRegion config;
        try {
            config = new FloorMapRegion(activeFloor, key, number.getText().trim(), type.getText().trim(),
                    Double.parseDouble(x.getText().trim()), Double.parseDouble(y.getText().trim()),
                    Double.parseDouble(width.getText().trim()), Double.parseDouble(height.getText().trim()),
                    roomBox.getValue() == null ? null : roomBox.getValue().id());
        } catch (NumberFormatException invalid) {
            Forms.showError(error, "Enter valid decimal coordinates between 0.0 and 1.0.");
            return;
        }
        Async.run(() -> {
            FloorMapService service = new FloorMapService();
            service.saveDraft(config);
            if (publish) service.publishDraft(activeFloor, key);
            return service.regions(activeFloor, coordinator);
        }, loaded -> {
            configuredRegions.put(activeFloor, indexRegions(loaded));
            showFloor();
            refreshAvailability();
            if (publish) {
                modal.close();
                Toast.success("Floor-map region validated and published.");
            } else {
                Toast.success("Calibration draft saved. Publish it when the geometry and room mapping are verified.");
            }
        }, failure -> Forms.showError(error, Errors.message(failure)));
    }

    private void showStatus(String message) {
        searchStatus.setText(message);
        searchStatus.setManaged(!message.isBlank());
        searchStatus.setVisible(!message.isBlank());
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static Map<String, FloorMapRegion> indexRegions(List<FloorMapRegion> regions) {
        Map<String, FloorMapRegion> indexed = new HashMap<>();
        regions.forEach(region -> indexed.put(region.key(), region));
        return indexed;
    }

    private static String regionKey(Floor floor, int index) {
        return floor.number() + ":region:" + index;
    }

    private void setZoom(double value) {
        zoom = Math.max(.1, Math.min(4, value));
        mapGroup.setScaleX(zoom);
        mapGroup.setScaleY(zoom);
        updateOverviewWindow();
        saveView();
    }

    private void zoomAt(double value, double x, double y) {
        if (mapImage.getImage() == null || mapViewport.getWidth() <= 0 || mapViewport.getHeight() <= 0) return;
        javafx.geometry.Point2D anchor = mapGroup.sceneToLocal(mapViewport.localToScene(x, y));
        setZoom(value);
        javafx.geometry.Point2D moved = mapViewport.sceneToLocal(mapGroup.localToScene(anchor));
        mapGroup.setTranslateX(mapGroup.getTranslateX() + x - moved.getX());
        mapGroup.setTranslateY(mapGroup.getTranslateY() + y - moved.getY());
        updateOverviewWindow();
        saveView();
    }

    private void zoomOnScroll(ScrollEvent event) {
        if (event.getDeltaY() != 0) {
            zoomAt(zoom * Math.pow(1.0015, event.getDeltaY()),
                    mapViewport.sceneToLocal(event.getSceneX(), event.getSceneY()).getX(),
                    mapViewport.sceneToLocal(event.getSceneX(), event.getSceneY()).getY());
            event.consume();
        }
    }

    private void fitMap() {
        if (floorPicker.getValue() == null || mapViewport.getWidth() <= 0 || mapViewport.getHeight() <= 0) return;
        Floor floor = FLOORS.stream().filter(f -> f.number() == floorPicker.getValue()).findFirst().orElseThrow();
        zoom = Math.min((mapViewport.getWidth() - 24) / floor.width(), (mapViewport.getHeight() - 24) / floor.height());
        zoom = Math.max(.1, Math.min(4, zoom));
        mapGroup.setScaleX(zoom);
        mapGroup.setScaleY(zoom);
        mapGroup.setTranslateX(0);
        mapGroup.setTranslateY(0);
        fitOnNextLayout = false;
        updateOverviewWindow();
        saveView();
    }

    private void updateClip() {
        if (mapViewport.getClip() instanceof Rectangle clip) {
            clip.setWidth(mapViewport.getWidth());
            clip.setHeight(mapViewport.getHeight());
        }
    }

    private void handleViewportResize() {
        updateClip();
        if (fitOnNextLayout) fitMap();
        else updateOverviewWindow();
    }

    private void saveView() {
        if (activeFloor != 0 && zoom > 0) {
            floorViews.put(activeFloor, new ViewState(zoom, mapGroup.getTranslateX(), mapGroup.getTranslateY()));
        }
    }

    private void updateRegionStyles() {
        for (Label label : regionLabels) label.setVisible(showLabels);
        for (int i = 0; i < regionShapes.size(); i++) {
            Rectangle shape = regionShapes.get(i);
            Region region = (Region) shape.getUserData();
            Room room = roomFor(activeFloor, region);
            RoomAvailability availability = room == null ? null : availabilityByRoomId.get(room.id());
            Color statusColor = statusColor(availability);
            boolean selected = region == selectedRegion;
            boolean known = availability != null;

            if (showAvailability) {
                Color fillColor = known ? statusColor : Color.web("#8A94A6");
                double opacity = selected ? .34 : .22;
                shape.setFill(Color.color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), opacity));
            } else {
                shape.setFill(selected ? Color.color(.12, .34, .88, .24)
                        : showBoundaries ? Color.color(.12, .34, .88, .035) : Color.TRANSPARENT);
            }

            if (selected) {
                shape.setStroke(Color.web("#0b3fc5"));
                shape.setStrokeWidth(4);
            } else if (showBoundaries) {
                shape.setStroke(showAvailability && known ? statusColor : Color.color(.08, .22, .72, .65));
                shape.setStrokeWidth(2.2);
            } else {
                shape.setStroke(Color.TRANSPARENT);
                shape.setStrokeWidth(2.2);
            }

            String status = availability == null ? "Unknown" : availability.status().label();
            String tooltipText = region.label() + " · Floor " + activeFloor + " · " + status;
            regionTooltips.get(i).setText(tooltipText);
            shape.setAccessibleText(tooltipText);
        }
    }

    private static Color statusColor(RoomAvailability availability) {
        if (availability == null) return Color.web("#8A94A6");
        return switch (availability.status()) {
            case AVAILABLE -> Color.web("#15803D");
            case OCCUPIED -> Color.web("#D04444");
            case RESERVED -> Color.web("#D99A19");
            case UNDER_MAINTENANCE, UNAVAILABLE -> Color.web("#8A94A6");
        };
    }

    private void updateOverviewWindow() {
        if (activeFloor == 0 || mapViewport.getWidth() <= 0 || zoom <= 0) return;
        Floor floor = FLOORS.stream().filter(f -> f.number() == activeFloor).findFirst().orElseThrow();
        double miniWidth = overviewImage.getFitWidth();
        double miniHeight = overviewImage.getFitHeight();
        if (miniWidth <= 0 || miniHeight <= 0) return;
        javafx.geometry.Point2D center = mapGroup.sceneToLocal(mapViewport.localToScene(
                mapViewport.getWidth() / 2, mapViewport.getHeight() / 2));
        double visibleWidth = Math.min(floor.width(), mapViewport.getWidth() / zoom);
        double visibleHeight = Math.min(floor.height(), mapViewport.getHeight() / zoom);
        double left = Math.max(0, Math.min(floor.width() - visibleWidth, center.getX() - visibleWidth / 2));
        double top = Math.max(0, Math.min(floor.height() - visibleHeight, center.getY() - visibleHeight / 2));
        double x = left / floor.width() * miniWidth;
        double y = top / floor.height() * miniHeight;
        double width = visibleWidth / floor.width() * miniWidth;
        double height = visibleHeight / floor.height() * miniHeight;
        overviewWindow.setWidth(width);
        overviewWindow.setHeight(height);
        overviewWindow.setTranslateX(x + width / 2 - miniWidth / 2);
        overviewWindow.setTranslateY(y + height / 2 - miniHeight / 2);
    }

    private void centerMapOnOverview(double sceneX, double sceneY) {
        if (activeFloor == 0) return;
        javafx.geometry.Point2D point = overview.sceneToLocal(sceneX, sceneY);
        Floor floor = FLOORS.stream().filter(f -> f.number() == activeFloor).findFirst().orElseThrow();
        double imageX = Math.max(0, Math.min(floor.width(), point.getX() / overview.getWidth() * floor.width()));
        double imageY = Math.max(0, Math.min(floor.height(), point.getY() / overview.getHeight() * floor.height()));
        javafx.geometry.Point2D current = mapViewport.sceneToLocal(mapGroup.localToScene(imageX, imageY));
        mapGroup.setTranslateX(mapGroup.getTranslateX() + mapViewport.getWidth() / 2 - current.getX());
        mapGroup.setTranslateY(mapGroup.getTranslateY() + mapViewport.getHeight() / 2 - current.getY());
        updateOverviewWindow();
        saveView();
    }

    private void clearSelection(String title) {
        selectedRegion = null;
        selectedTitle.setText(title);
        selectedInfo.setText("Select a mapped location to see verified room information.");
        roomDetailsButton.setUserData(null);
        setRoomActionsEnabled(false);
        calibrationButton.setDisable(true);
        updateRegionStyles();
        searchSuggestions.hide();
        searchStatus.setText("");
        searchStatus.setManaged(false);
        searchStatus.setVisible(false);
    }

    private void updateSuggestions(String input) {
        searchSuggestions.hide();
        searchSuggestions.getItems().clear();
        String query = RoomLabelMatcher.normalize(input);
        if (query.isEmpty() && !hasSearchFilters()) return;
        int parsedCapacity = 0;
        if (!capacityFilter.getText().isBlank()) {
            try {
                parsedCapacity = Integer.parseInt(capacityFilter.getText().trim());
                if (parsedCapacity < 0) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                showStatus("Minimum capacity must be a non-negative whole number.");
                return;
            }
        }
        int minimumCapacity = parsedCapacity;
        List<Region> results = FLOORS.stream().flatMap(floor -> floor.regions().stream()
                .filter(region -> query.isEmpty() || matchesSearch(floor.number(), region, query))
                .filter(region -> matchesFilters(floor.number(), region, minimumCapacity)))
                .limit(8).toList();
        if (results.isEmpty()) {
            String description = query.isEmpty() ? "the selected filters"
                    : "\"" + input.trim() + "\" and the selected filters";
            searchStatus.setText("No mapped location matches " + description + ".");
            searchStatus.setManaged(true);
            searchStatus.setVisible(true);
            return;
        }
        searchStatus.setText("");
        searchStatus.setManaged(false);
        searchStatus.setVisible(false);
        for (Region region : results) {
            int floor = findFloor(region);
            MenuItem item = new MenuItem(region.label() + " · Floor " + floor);
            item.setOnAction(e -> {
                searchField.setText(region.label());
                floorPicker.setValue(floor);
                selectSearchResult(region);
            });
            searchSuggestions.getItems().add(item);
        }
        searchSuggestions.show(searchField, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private boolean hasSearchFilters() {
        return (searchFloorFilter.getValue() != null && !searchFloorFilter.getValue().equals("Any floor"))
                || (typeFilter.getValue() != null && !typeFilter.getValue().equals("All types"))
                || !capacityFilter.getText().isBlank()
                || !equipmentFilter.getText().isBlank()
                || availableOnlyFilter.isSelected();
    }

    private boolean matchesSearch(int floor, Region region, String query) {
        String aliases = switch (region.type()) {
            case "Staircase" -> " stairs staircase stairs";
            case "Lift" -> " elevator lift elevators";
            case "Washroom" -> " toilet restroom washroom";
            default -> "";
        };
        String searchable = RoomLabelMatcher.normalize(region.label() + " " + floor + " floor Floor " + floor + aliases);
        if (searchable.contains(query)) return true;
        Room room = roomFor(floor, region);
        return room != null && RoomLabelMatcher.normalize(room.name() + " " + room.facilities()).contains(query);
    }

    private boolean matchesFilters(int floor, Region region, int minimumCapacity) {
        String floorSelection = searchFloorFilter.getValue();
        if (floorSelection != null && !floorSelection.equals("Any floor")
                && !floorSelection.equals("Floor " + floor)) return false;

        Room room = roomFor(floor, region);
        String selectedType = typeFilter.getValue();
        if (selectedType != null && !selectedType.equals("All types")
                && !matchesTypeFilter(selectedType, region, room)) return false;

        if (minimumCapacity > 0 && (room == null || room.capacity() < minimumCapacity)) return false;

        String equipment = RoomLabelMatcher.normalize(equipmentFilter.getText());
        if (!equipment.isEmpty() && (room == null
                || !RoomLabelMatcher.normalize(room.facilities()).contains(equipment))) return false;

        if (availableOnlyFilter.isSelected()) {
            RoomAvailability availability = room == null ? null : availabilityByRoomId.get(room.id());
            if (availability == null || availability.status() != RoomStatus.AVAILABLE) return false;
        }
        return true;
    }

    private static boolean matchesTypeFilter(String selectedType, Region region, Room room) {
        RoomType roomType = room == null ? null : room.type();
        return switch (selectedType) {
            case "Classroom" -> roomType == null
                    ? region.type().equalsIgnoreCase("Classroom") : roomType == RoomType.CLASSROOM;
            case "Computer lab" -> roomType == RoomType.COMPUTER_LAB;
            case "Laboratory" -> roomType == null
                    ? region.type().equalsIgnoreCase("Laboratory") : roomType == RoomType.LABORATORY;
            case "Seminar hall" -> roomType == null
                    ? region.type().equalsIgnoreCase("Seminar hall") : roomType == RoomType.SEMINAR_ROOM;
            case "Library" -> region.type().toLowerCase(Locale.ROOT).contains("library");
            case "Office" -> region.type().toLowerCase(Locale.ROOT).contains("office")
                    || region.type().equalsIgnoreCase("IT-HOD")
                    || region.type().equalsIgnoreCase("Staff room");
            case "Other facilities" -> roomType == null
                    && !region.type().toLowerCase(Locale.ROOT).contains("library")
                    && !region.type().toLowerCase(Locale.ROOT).contains("office")
                    && !region.type().equalsIgnoreCase("IT-HOD")
                    && !region.type().equalsIgnoreCase("Staff room")
                    && !List.of("Classroom", "Laboratory", "Seminar hall").contains(region.type());
            default -> true;
        };
    }

    private int findFloor(Region target) {
        return FLOORS.stream().filter(floor -> floor.regions().contains(target))
                .map(Floor::number).findFirst().orElse(activeFloor);
    }

    private void selectSearchResult(Region region) {
        int floorNumber = findFloor(region);
        Floor floor = FLOORS.stream().filter(candidate -> candidate.number() == floorNumber).findFirst().orElseThrow();
        int sourceIndex = floor.regions().indexOf(region);
        if (sourceIndex < 0) return;
        String key = regionKey(floor, sourceIndex);
        if (floorNumber != activeFloor) floorPicker.setValue(floorNumber);
        Region displayed = regionShapes.stream().map(shape -> (Region) shape.getUserData())
                .filter(candidate -> key.equals(regionKeys.get(candidate))).findFirst().orElse(null);
        if (displayed == null) return;
        select(displayed);
        floor = FLOORS.stream().filter(candidate -> candidate.number() == activeFloor).findFirst().orElseThrow();
        double centerX = (displayed.x() + displayed.width() / 2) * floor.width();
        double centerY = (displayed.y() + displayed.height() / 2) * floor.height();
        if (mapViewport.getWidth() > 0 && mapViewport.getHeight() > 0) {
            javafx.geometry.Point2D current = mapViewport.sceneToLocal(mapGroup.localToScene(centerX, centerY));
            mapGroup.setTranslateX(mapGroup.getTranslateX() + mapViewport.getWidth() / 2 - current.getX());
            mapGroup.setTranslateY(mapGroup.getTranslateY() + mapViewport.getHeight() / 2 - current.getY());
            zoomAt(Math.max(zoom * 1.8, 1.2), mapViewport.getWidth() / 2, mapViewport.getHeight() / 2);
        }
        saveView();
    }

    private Room roomFor(int floor, Region region) {
        Floor sourceFloor = FLOORS.stream().filter(candidate -> candidate.number() == floor).findFirst().orElseThrow();
        String key = regionKeys.get(region);
        if (key == null) {
            int sourceIndex = sourceFloor.regions().indexOf(region);
            if (sourceIndex < 0) return null;
            key = regionKey(sourceFloor, sourceIndex);
        }
        FloorMapRegion mapping = configuredRegions.getOrDefault(floor, Map.of()).get(key);
        if (mapping != null) {
            if (mapping.roomId() == null) return null;
            return rooms.stream().filter(room -> room.id() == mapping.roomId() && room.floor() == floor)
                    .findFirst().orElse(null);
        }
        List<Room> matches = RoomLabelMatcher.matches(rooms, floor, region.number(), region.type());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static HBox legendItem(String color, String text) {
        Circle dot = new Circle(5, Color.web(color));
        Label label = new Label(text);
        label.getStyleClass().add("muted-small");
        HBox item = new HBox(6, dot, label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void chooseFirstSuggestion() {
        if (!searchSuggestions.getItems().isEmpty()) searchSuggestions.getItems().get(0).fire();
        else {
            String query = searchField.getText().trim();
            searchStatus.setText(query.isEmpty() ? "No locations match the selected filters."
                    : "No mapped location matches \"" + query + "\".");
            searchStatus.setManaged(true);
            searchStatus.setVisible(true);
        }
    }
}