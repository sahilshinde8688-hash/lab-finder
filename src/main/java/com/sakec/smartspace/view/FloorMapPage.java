package com.sakec.smartspace.view;

import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.FloorMapRegion;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.service.AvailabilityService;
import com.sakec.smartspace.service.FloorMapService;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.util.RoomLabelMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Interactive, image-based floor map. Region coordinates are normalized against the supplied source images. */
public class FloorMapPage extends Page {
    private record Region(String number, String type, double x, double y, double width, double height) {
        String label() { return type + " " + number; }
    }
    private record Floor(int number, String image, double width, double height, List<Region> regions) { }
    private record ViewState(double zoom, double translateX, double translateY) { }
    private record PageData(List<Room> rooms, List<FloorMapRegion> fourth, List<FloorMapRegion> fifth) { }

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
    private final ImageView mapImage = new ImageView();
    private final Pane regionLayer = new Pane();
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
    private final Button reportButton = Forms.secondary("Report a problem");
    private final Button calibrationButton = Forms.secondary("Calibrate region");
    private final List<Label> regionLabels = new ArrayList<>();
    private final List<Rectangle> regionShapes = new ArrayList<>();
    private final Map<Integer, ViewState> floorViews = new HashMap<>();
    private final Map<Integer, Image> floorImages = new HashMap<>();
    private final Map<Integer, Map<String, FloorMapRegion>> configuredRegions = new HashMap<>();
    private final Map<Region, String> regionKeys = new HashMap<>();
    private List<Room> rooms = List.of();
    private Region selectedRegion;
    private int activeFloor;
    private int availabilityRequest;
    private double zoom = 1;
    private double pressX;
    private double pressY;
    private double dragX;
    private double dragY;
    private boolean dragged;
    private boolean showBoundaries = true;
    private boolean showLabels = true;
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
        zoomIn.setOnAction(e -> zoomAt(zoom * 1.2, mapViewport.getWidth() / 2, mapViewport.getHeight() / 2));
        zoomOut.setOnAction(e -> zoomAt(zoom / 1.2, mapViewport.getWidth() / 2, mapViewport.getHeight() / 2));
        fit.setOnAction(e -> fitMap());
        reset.setOnAction(e -> {
            mapGroup.setTranslateX(0);
            mapGroup.setTranslateY(0);
            fitMap();
            saveView();
        });

        searchField.setPromptText("Search rooms, facilities, or equipment");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((o, oldValue, value) -> updateSuggestions(value));
        searchField.setOnAction(e -> chooseFirstSuggestion());
        CheckBox boundaries = new CheckBox("Boundaries");
        boundaries.setSelected(true);
        boundaries.selectedProperty().addListener((o, oldValue, value) -> {
            showBoundaries = value;
            updateOverlayVisibility();
        });
        CheckBox labels = new CheckBox("Labels");
        labels.setSelected(true);
        labels.selectedProperty().addListener((o, oldValue, value) -> {
            showLabels = value;
            updateOverlayVisibility();
        });
        calibrationButton.setVisible(coordinator);
        calibrationButton.setManaged(coordinator);
        calibrationButton.setDisable(true);
        calibrationButton.setOnAction(e -> toggleCalibrationDrawMode());
        HBox toolbar = new HBox(8, floorPicker, searchField, zoomOut, zoomIn, fit, reset, calibrationButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox filters = new HBox(12, boundaries, labels, Forms.muted("Green = available · Red = occupied/reserved · Grey = unknown"));
        filters.setAlignment(Pos.CENTER_LEFT);
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
        mapGroup.getChildren().addAll(mapImage, regionLayer);
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
        routeButton.setOnAction(e -> Toast.info("Indoor routes are unavailable until a coordinator configures and verifies corridor connections and floor transitions."));
        reportButton.setDisable(true);
        reportButton.setOnAction(e -> {
            Room room = selectedRoom();
            if (room != null) Dialogs.report(room, this::show);
        });
        Label note = new Label("Availability is timetable-based, not physical occupancy. Unknown or unconfigured map labels are never shown as available.");
        note.setWrapText(true);
        note.getStyleClass().add("muted");
        details.setPadding(new Insets(18));
        details.getStyleClass().add("card");
        details.getChildren().addAll(selectedTitle, selectedInfo, roomDetailsButton, timetableButton, routeButton, reportButton, note);
        details.setMinWidth(230);
        details.setPrefWidth(270);

        HBox workspace = new HBox(16, mapViewport, details);
        HBox.setHgrow(mapViewport, Priority.ALWAYS);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        body.getChildren().addAll(Forms.pageHeader("Interactive floor map", "Explore verified room labels on the fourth and fifth floors."),
                toolbar, filters, searchStatus, workspace);
        showFloor();
    }

    @Override public String title() { return "Floor map"; }

    @Override public void show() {
        Async.run(() -> {
            RoomDao roomDao = new RoomDao();
            FloorMapService mapService = new FloorMapService();
            return new PageData(roomDao.listActive(), mapService.regions(4, coordinator), mapService.regions(5, coordinator));
        }, loaded -> {
            rooms = loaded.rooms();
            configuredRegions.put(4, indexRegions(loaded.fourth()));
            configuredRegions.put(5, indexRegions(loaded.fifth()));
            showFloor();
        },
                error -> Toast.error(Errors.message(error)));
    }

    private void showFloor() {
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
        regionLayer.getChildren().clear();
        regionShapes.clear();
        regionLabels.clear();
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
        updateOverlayVisibility();
        updateOverviewWindow();
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
        shape.setOnMouseEntered(e -> {
            if (showBoundaries && selectedRegion != region) {
                shape.setFill(Color.color(.12, .34, .88, .22));
                shape.setStroke(Color.web("#0b3fc5"));
            }
        });
        shape.setOnMouseExited(e -> {
            if (showBoundaries && selectedRegion != region) {
                shape.setFill(Color.color(.12, .34, .88, .04));
                shape.setStroke(Color.color(.08, .22, .72, .56));
            }
        });
        shape.setOnMouseClicked(e -> { if (!dragged) select(region); e.consume(); });
        Tooltip.install(shape, new Tooltip(region.label() + " · Floor " + floor.number()));
        regionLayer.getChildren().add(shape);
        regionShapes.add(shape);

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
        for (int i = 0; i < regionShapes.size(); i++) {
            Rectangle shape = regionShapes.get(i);
            boolean selected = shape.getUserData() == region;
            shape.setFill(Color.color(.12, .34, .88, selected ? .38 : .04));
            shape.setStroke(selected ? Color.web("#0b3fc5") : Color.color(.08, .22, .72, .56));
            shape.setStrokeWidth(selected ? 4 : 2.2);
        }
        selectedTitle.setText(region.label() + " · Floor " + activeFloor);
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
        Room selectedRoom = match;
        int request = ++availabilityRequest;
        setRoomActionsEnabled(false);
        selectedInfo.setText(selectedRoom.name() + " · " + selectedRoom.type().label() + " · Floor " + selectedRoom.floor()
                + "\nCapacity: " + selectedRoom.capacity() + " · Equipment: "
                + (selectedRoom.facilityList().isEmpty() ? "Not recorded" : String.join(", ", selectedRoom.facilityList()))
                + "\nAvailability: Checking current timetable and reservations…");
        Async.run(() -> new AvailabilityService().check(selectedRoom.id(), java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now().plusMinutes(1)), availability -> {
                    if (request != availabilityRequest || selectedRegion != region) return;
                    selectedInfo.setText(selectedRoom.name() + " · " + selectedRoom.type().label() + " · Floor " + selectedRoom.floor()
                            + "\nCapacity: " + selectedRoom.capacity() + " · Equipment: "
                            + (selectedRoom.facilityList().isEmpty() ? "Not recorded" : String.join(", ", selectedRoom.facilityList()))
                            + "\nAvailability: " + availability.status().label() + " · " + availability.detail()
                            + "\nVerification: matched to a unique active room record.");
                    roomDetailsButton.setUserData(availability);
                    setRoomActionsEnabled(true);
                    updateSelectedAvailabilityColor(availability);
                }, error -> {
                    if (request != availabilityRequest || selectedRegion != region) return;
                    selectedInfo.setText(selectedRoom.name() + " · " + selectedRoom.type().label() + " · Floor " + selectedRoom.floor()
                            + "\nAvailability: Unknown\n" + Errors.message(error));
                    setRoomActionsEnabled(false);
                });
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
        roomDetailsButton.setDisable(!enabled);
        timetableButton.setDisable(!enabled || !canViewTimetable || navigator == null);
        reportButton.setDisable(!enabled);
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

    private void updateOverlayVisibility() {
        for (Rectangle shape : regionShapes) {
            shape.setStroke(showBoundaries ? Color.color(.08, .22, .72, .72) : Color.TRANSPARENT);
            shape.setFill(showBoundaries ? Color.color(.12, .34, .88, .04) : Color.TRANSPARENT);
        }
        for (Label label : regionLabels) label.setVisible(showLabels);
        if (selectedRegion != null) {
            for (int i = 0; i < regionShapes.size(); i++) {
                if (regionShapes.get(i).getUserData() == selectedRegion) {
                    regionShapes.get(i).setFill(Color.color(.12, .34, .88, .38));
                    regionShapes.get(i).setStroke(showBoundaries ? Color.web("#0b3fc5") : Color.TRANSPARENT);
                    regionShapes.get(i).setStrokeWidth(4);
                }
            }
        }
    }

    private void updateSelectedAvailabilityColor(RoomAvailability availability) {
        Color color = switch (availability.status()) {
            case AVAILABLE -> Color.web("#15803d");
            case OCCUPIED, RESERVED, UNDER_MAINTENANCE -> Color.web("#b42318");
            case UNAVAILABLE -> Color.web("#667085");
        };
        for (Rectangle shape : regionShapes) {
            if (shape.getUserData() == selectedRegion) {
                shape.setFill(showBoundaries ? Color.color(color.getRed(), color.getGreen(), color.getBlue(), .25) : Color.TRANSPARENT);
                shape.setStroke(showBoundaries ? color : Color.TRANSPARENT);
            }
        }
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
        availabilityRequest++;
        selectedRegion = null;
        selectedTitle.setText(title);
        selectedInfo.setText("Select a mapped location to see verified room information.");
        roomDetailsButton.setUserData(null);
        setRoomActionsEnabled(false);
        calibrationButton.setDisable(true);
        searchSuggestions.hide();
        searchStatus.setText("");
        searchStatus.setManaged(false);
        searchStatus.setVisible(false);
    }

    private void updateSuggestions(String input) {
        searchSuggestions.hide();
        searchSuggestions.getItems().clear();
        if (input == null || input.isBlank()) return;
        String query = RoomLabelMatcher.normalize(input);
        List<Region> results = FLOORS.stream().flatMap(floor -> floor.regions().stream()
                .filter(region -> matchesSearch(floor.number(), region, query)))
                .limit(8).toList();
        if (results.isEmpty()) {
            searchStatus.setText("No mapped location matches \"" + input.trim() + "\".");
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
        int sourceIndex = sourceFloor.regions().indexOf(region);
        if (sourceIndex < 0) return null;
        FloorMapRegion mapping = configuredRegions.getOrDefault(floor, Map.of()).get(regionKey(sourceFloor, sourceIndex));
        if (mapping != null) {
            if (mapping.roomId() == null) return null;
            return rooms.stream().filter(room -> room.id() == mapping.roomId() && room.floor() == floor)
                    .findFirst().orElse(null);
        }
        List<Room> matches = RoomLabelMatcher.matches(rooms, floor, region.number(), region.type());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private void chooseFirstSuggestion() {
        if (!searchSuggestions.getItems().isEmpty()) searchSuggestions.getItems().get(0).fire();
        else {
            searchStatus.setText("No mapped location matches \"" + searchField.getText().trim() + "\".");
            searchStatus.setManaged(true);
            searchStatus.setVisible(true);
        }
    }
}