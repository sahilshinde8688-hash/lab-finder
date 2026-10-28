package com.sakec.smartspace.view;

import com.sakec.smartspace.model.NavigationEdge;
import com.sakec.smartspace.model.NavigationEdgeType;
import com.sakec.smartspace.model.NavigationGraph;
import com.sakec.smartspace.model.NavigationNode;
import com.sakec.smartspace.model.NavigationNodeType;
import com.sakec.smartspace.model.NavigationRoomLink;
import com.sakec.smartspace.model.NavigationValidation;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.service.NavigationService;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Errors;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/** Coordinator editor for draft indoor-navigation data. */
final class NavigationManagementDialog {
    private final NavigationService service;
    private final List<Room> rooms;
    private final Consumer<NavigationGraph> graphChanged;
    private final ComboBox<Integer> floor = new ComboBox<>();
    private final ComboBox<NavigationNodeType> nodeType = new ComboBox<>();
    private final TextField nodeLabel = new TextField();
    private final CheckBox nodeVerified = new CheckBox("Site-verified node");
    private final Label placementStatus = Forms.muted("Choose a floor and place a node on its image.");
    private final ListView<NavigationNode> nodesList = new ListView<>();
    private final ComboBox<NavigationNode> fromNode = new ComboBox<>();
    private final ComboBox<NavigationNode> toNode = new ComboBox<>();
    private final ComboBox<NavigationEdgeType> edgeType = new ComboBox<>();
    private final TextField distance = new TextField();
    private final CheckBox distanceVerified = new CheckBox("Distance verified");
    private final CheckBox accessible = new CheckBox("Accessible");
    private final CheckBox accessVerified = new CheckBox("Accessibility verified");
    private final CheckBox edgeVerified = new CheckBox("Connection verified");
    private final ListView<NavigationEdge> edgesList = new ListView<>();
    private final ComboBox<Room> roomChoice = new ComboBox<>();
    private final ComboBox<NavigationNode> roomNodeChoice = new ComboBox<>();
    private final ListView<NavigationRoomLink> linksList = new ListView<>();
    private final TextArea diagnostics = new TextArea();
    private final ImageView previewImage = new ImageView();
    private final Pane previewLayer = new Pane();
    private final StackPane preview = new StackPane(previewImage, previewLayer);
    private final Modal modal;
    private NavigationGraph graph;
    private String selectedNodeId;
    private String selectedEdgeId;
    private boolean placingNode;

    NavigationManagementDialog(NavigationService service, List<Room> rooms,
                               Consumer<NavigationGraph> graphChanged) {
        this.service = service;
        this.rooms = rooms.stream().filter(r -> r.floor() == 4 || r.floor() == 5)
                .sorted(Comparator.comparingInt(Room::floor).thenComparing(Room::name)).toList();
        this.graphChanged = graphChanged;
        this.graph = service.draft();

        floor.getItems().setAll(4, 5);
        floor.setValue(4);
        floor.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer value) { return value == null ? "" : "Floor " + value; }
            @Override public Integer fromString(String value) { return Integer.valueOf(value.replace("Floor ", "")); }
        });
        nodeType.getItems().setAll(NavigationNodeType.values());
        nodeType.setValue(NavigationNodeType.CORRIDOR);
        nodeLabel.setPromptText("e.g. North corridor junction");
        edgeType.getItems().setAll(NavigationEdgeType.values());
        edgeType.setValue(NavigationEdgeType.WALK);
        distance.setPromptText("Optional, verified metres");
        roomChoice.getItems().setAll(this.rooms);
        diagnostics.setEditable(false);
        diagnostics.setWrapText(true);
        diagnostics.setPrefRowCount(5);
        diagnostics.setPromptText("Run validation to see missing or invalid connections and affected rooms.");

        nodesList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(NavigationNode node, boolean empty) {
                super.updateItem(node, empty);
                setText(empty || node == null ? null : node.label() + " · Floor " + node.floor()
                        + " · " + node.type().label() + (node.verified() ? " · verified" : " · unverified"));
            }
        });
        edgesList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(NavigationEdge edge, boolean empty) {
                super.updateItem(edge, empty);
                setText(empty || edge == null ? null : nodeName(edge.fromNodeId()) + " ↔ "
                        + nodeName(edge.toNodeId()) + " · " + edge.type().label()
                        + (edge.verified() ? " · verified" : " · unverified"));
            }
        });
        linksList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(NavigationRoomLink link, boolean empty) {
                super.updateItem(link, empty);
                Room room = empty || link == null ? null : roomById(link.roomId());
                setText(room == null ? null : room.name() + " · Floor " + room.floor()
                        + " → " + nodeName(link.nodeId()));
            }
        });
        fromNode.setConverter(nodeConverter());
        toNode.setConverter(nodeConverter());
        roomNodeChoice.setConverter(nodeConverter());

        floor.valueProperty().addListener((o, a, b) -> renderPreview());
        nodesList.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> loadNode(selected));
        edgesList.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> loadEdge(selected));
        preview.setOnMouseClicked(event -> placeOrMoveNode(event.getX(), event.getY()));

        TabPane editors = new TabPane(nodeTab(), edgeTab(), roomLinkTab());
        editors.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox previewColumn = new VBox(8, Forms.field("Floor plan", floor), preview, placementStatus);
        previewColumn.setPadding(new Insets(8));
        VBox.setVgrow(preview, Priority.ALWAYS);
        preview.setMinSize(400, 290);
        preview.setPrefSize(480, 330);
        preview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        previewImage.setPreserveRatio(false);
        previewImage.fitWidthProperty().bind(preview.widthProperty());
        previewImage.fitHeightProperty().bind(preview.heightProperty());
        previewLayer.prefWidthProperty().bind(preview.widthProperty());
        previewLayer.prefHeightProperty().bind(preview.heightProperty());
        previewLayer.setMouseTransparent(true);
        preview.setStyle("-fx-background-color: #e8edf3; -fx-border-color: #d5dce5; -fx-border-width: 1;");
        HBox workArea = new HBox(12, previewColumn, editors);
        workArea.setPrefHeight(390);
        HBox.setHgrow(previewColumn, Priority.ALWAYS);
        HBox.setHgrow(editors, Priority.ALWAYS);
        workArea.setPrefWidth(930);

        Button validate = Forms.secondary("Validate navigation network");
        validate.setOnAction(e -> validateDraft());
        Button publish = Forms.primary("Validate and publish");
        publish.setOnAction(e -> publishDraft());
        HBox actions = new HBox(8, validate, publish);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Label note = Forms.muted("Only coordinator-entered and verified connections can be used for routes. "
                + "Click the floor-plan preview to place or reposition the selected node.");
        VBox content = new VBox(12, note, workArea, Forms.field("Validation diagnostics", diagnostics), actions);
        content.setPadding(new Insets(4));
        modal = new Modal("Indoor Navigation Management", content).width(1000);
        updateGraph(graph);
        renderPreview();
    }

    void show() { modal.show(); }

    private Tab nodeTab() {
        Button add = Forms.secondary("Add node on map");
        Button place = Forms.secondary("Reposition selected");
        Button save = Forms.primary("Save node");
        Button delete = Forms.danger("Delete node");
        add.setOnAction(e -> {
            selectedNodeId = null;
            nodesList.getSelectionModel().clearSelection();
            nodeLabel.clear();
            nodeVerified.setSelected(false);
            placingNode = true;
            placementStatus.setText("Enter the label and click the floor plan to add a node.");
        });
        place.setOnAction(e -> {
            if (selectedNodeId == null) {
                Toast.info("Select a node to reposition, or use Add node on map.");
                return;
            }
            placingNode = true;
            placementStatus.setText("Click the plan to reposition the selected node.");
        });
        save.setOnAction(e -> saveNode());
        delete.setOnAction(e -> deleteNode());
        VBox fields = new VBox(8, Forms.field("Node type", nodeType), Forms.field("Label", nodeLabel),
                nodeVerified, new HBox(8, add, place), new HBox(8, save, delete), nodesList);
        VBox.setVgrow(nodesList, Priority.ALWAYS);
        fields.setPadding(new Insets(10));
        fields.setPrefWidth(420);
        return new Tab("Nodes", fields);
    }

    private Tab edgeTab() {
        Button save = Forms.primary("Save connection");
        Button delete = Forms.danger("Delete connection");
        save.setOnAction(e -> saveEdge());
        delete.setOnAction(e -> deleteEdge());
        VBox fields = new VBox(8, Forms.field("From node", fromNode), Forms.field("To node", toNode),
                Forms.field("Connection type", edgeType), Forms.field("Distance (metres)", distance), distanceVerified,
                accessible, accessVerified, edgeVerified, new HBox(8, save, delete), edgesList);
        VBox.setVgrow(edgesList, Priority.ALWAYS);
        fields.setPadding(new Insets(10));
        fields.setPrefWidth(420);
        return new Tab("Connections", fields);
    }

    private Tab roomLinkTab() {
        Button save = Forms.primary("Link room to corridor");
        Button delete = Forms.danger("Remove room link");
        save.setOnAction(e -> saveRoomLink());
        delete.setOnAction(e -> deleteRoomLink());
        VBox fields = new VBox(8, Forms.field("Room", roomChoice), Forms.field("Corridor node", roomNodeChoice),
                new HBox(8, save, delete), linksList);
        VBox.setVgrow(linksList, Priority.ALWAYS);
        fields.setPadding(new Insets(10));
        fields.setPrefWidth(420);
        return new Tab("Room links", fields);
    }

    private javafx.util.StringConverter<NavigationNode> nodeConverter() {
        return new javafx.util.StringConverter<>() {
            @Override public String toString(NavigationNode node) { return node == null ? "" : node.toString(); }
            @Override public NavigationNode fromString(String value) { return null; }
        };
    }

    private void placeOrMoveNode(double x, double y) {
        if (!placingNode) return;
        if (nodeLabel.getText().isBlank()) {
            Toast.info("Enter a node label before placing it on the map.");
            return;
        }
        try {
            int currentFloor = floor.getValue();
            NavigationNode selected = nodesList.getSelectionModel().getSelectedItem();
            boolean moveExisting = selectedNodeId != null && selected != null && selected.floor() == currentFloor;
            String id = moveExisting ? selected.id() : UUID.randomUUID().toString();
            NavigationNode node = new NavigationNode(id, currentFloor, nodeType.getValue(), nodeLabel.getText().trim(),
                    Math.max(0, Math.min(1, x / preview.getWidth())),
                    Math.max(0, Math.min(1, y / preview.getHeight())), nodeVerified.isSelected());
            service.saveNode(node);
            placingNode = false;
            placementStatus.setText(String.format("Placed at %.3f, %.3f on Floor %d.", node.x(), node.y(), currentFloor));
            refreshDraft();
            selectNode(id);
        } catch (RuntimeException error) {
            Toast.error(Errors.message(error));
        }
    }

    private void saveNode() {
        try {
            if (nodeLabel.getText().isBlank()) {
                Toast.info("Enter a node label and place it on the floor plan.");
                return;
            }
            if (selectedNodeId == null) {
                if (!placingNode) {
                    placingNode = true;
                    placementStatus.setText("Click the floor plan to set the new node position.");
                }
                return;
            }
            NavigationNode current = graph.nodes().stream().filter(n -> n.id().equals(selectedNodeId)).findFirst().orElseThrow();
            NavigationNode updated = new NavigationNode(current.id(), floor.getValue(), nodeType.getValue(),
                    nodeLabel.getText().trim(), current.x(), current.y(), nodeVerified.isSelected());
            service.saveNode(updated);
            refreshDraft();
            selectNode(updated.id());
        } catch (RuntimeException error) {
            Toast.error(Errors.message(error));
        }
    }

    private void deleteNode() {
        NavigationNode selected = nodesList.getSelectionModel().getSelectedItem();
        if (selected == null) { Toast.info("Select a node to delete."); return; }
        try {
            service.deleteNode(selected.id());
            selectedNodeId = null;
            refreshDraft();
        } catch (RuntimeException error) { Toast.error(Errors.message(error)); }
    }

    private void loadNode(NavigationNode node) {
        selectedNodeId = node == null ? null : node.id();
        if (node == null) return;
        floor.setValue(node.floor());
        nodeType.setValue(node.type());
        nodeLabel.setText(node.label());
        nodeVerified.setSelected(node.verified());
        renderPreview();
    }

    private void saveEdge() {
        try {
            if (fromNode.getValue() == null || toNode.getValue() == null || edgeType.getValue() == null) {
                Toast.info("Choose both endpoint nodes and a connection type.");
                return;
            }
            Double metres = distance.getText().isBlank() ? null : Double.valueOf(distance.getText().trim());
            String id = selectedEdgeId == null ? UUID.randomUUID().toString() : selectedEdgeId;
            service.saveEdge(new NavigationEdge(id, fromNode.getValue().id(), toNode.getValue().id(),
                    edgeType.getValue(), metres, distanceVerified.isSelected(),
                    accessible.isSelected(), accessVerified.isSelected(),
                    edgeVerified.isSelected()));
            refreshDraft();
        } catch (NumberFormatException invalid) {
            Toast.error("Enter a valid positive distance or leave it blank.");
        } catch (RuntimeException error) { Toast.error(Errors.message(error)); }
    }

    private void deleteEdge() {
        NavigationEdge selected = edgesList.getSelectionModel().getSelectedItem();
        if (selected == null) { Toast.info("Select a connection to delete."); return; }
        try {
            service.deleteEdge(selected.id());
            selectedEdgeId = null;
            refreshDraft();
        } catch (RuntimeException error) { Toast.error(Errors.message(error)); }
    }

    private void loadEdge(NavigationEdge edge) {
        selectedEdgeId = edge == null ? null : edge.id();
        if (edge == null) return;
        fromNode.setValue(findNode(edge.fromNodeId()));
        toNode.setValue(findNode(edge.toNodeId()));
        edgeType.setValue(edge.type());
        distance.setText(edge.distanceMeters() == null ? "" : Double.toString(edge.distanceMeters()));
        distanceVerified.setSelected(edge.distanceVerified());
        accessible.setSelected(edge.accessible());
        accessVerified.setSelected(edge.accessibilityVerified());
        edgeVerified.setSelected(edge.verified());
    }

    private void saveRoomLink() {
        if (roomChoice.getValue() == null || roomNodeChoice.getValue() == null) {
            Toast.info("Choose a room and a corridor node.");
            return;
        }
        try {
            service.saveRoomLink(new NavigationRoomLink(roomChoice.getValue().id(), roomNodeChoice.getValue().id()));
            refreshDraft();
        } catch (RuntimeException error) { Toast.error(Errors.message(error)); }
    }

    private void deleteRoomLink() {
        NavigationRoomLink selected = linksList.getSelectionModel().getSelectedItem();
        if (selected == null) { Toast.info("Select a room link to remove."); return; }
        try {
            service.deleteRoomLink(selected.roomId());
            refreshDraft();
        } catch (RuntimeException error) { Toast.error(Errors.message(error)); }
    }

    private void validateDraft() {
        try {
            NavigationValidation result = service.validateDraft();
            diagnostics.setText(result.valid() ? "Validation passed. The draft can be published."
                    : result.issues().stream().map(issue -> "• " + issue.message())
                            .collect(java.util.stream.Collectors.joining("\n")));
            if (result.valid()) Toast.success("Navigation graph validation passed.");
        } catch (RuntimeException error) {
            diagnostics.setText("Validation could not run: " + Errors.message(error));
            Toast.error(Errors.message(error));
        }
    }

    private void publishDraft() {
        try {
            NavigationValidation result = service.validateDraft();
            if (!result.valid()) {
                diagnostics.setText(result.issues().stream().map(issue -> "• " + issue.message())
                        .collect(java.util.stream.Collectors.joining("\n")));
                Toast.error("Navigation graph failed validation. Review the diagnostics before publishing.");
                return;
            }
            service.publish();
            graph = service.published();
            graphChanged.accept(graph);
            diagnostics.setText("Published and validated navigation revision " + graph.revisionId() + ".");
            Toast.success("Validated navigation graph published.");
            refreshDraft();
        } catch (RuntimeException error) {
            diagnostics.setText("Publish failed: " + Errors.message(error));
            Toast.error(Errors.message(error));
        }
    }

    private void refreshDraft() {
        updateGraph(service.draft());
        renderPreview();
    }

    private void updateGraph(NavigationGraph updated) {
        graph = updated;
        List<NavigationNode> sortedNodes = graph.nodes().stream()
                .sorted(Comparator.comparingInt(NavigationNode::floor).thenComparing(NavigationNode::label)).toList();
        nodesList.getItems().setAll(sortedNodes);
        fromNode.getItems().setAll(sortedNodes);
        toNode.getItems().setAll(sortedNodes);
        roomNodeChoice.getItems().setAll(sortedNodes);
        edgesList.getItems().setAll(graph.edges());
        linksList.getItems().setAll(graph.roomLinks());
        graphChanged.accept(graph);
        renderPreview();
    }

    private void renderPreview() {
        if (floor.getValue() == null) return;
        int selectedFloor = floor.getValue();
        String imagePath = selectedFloor == 4 ? "/images/fourth_floor.png" : "/images/fifth_floor.png";
        try (var stream = getClass().getResourceAsStream(imagePath)) {
            if (stream == null) throw new IllegalStateException("The selected floor-plan image is missing.");
            previewImage.setImage(new Image(stream));
        } catch (java.io.IOException error) {
            Toast.error("The selected floor-plan image could not be read.");
            return;
        }
        previewLayer.getChildren().clear();
        previewLayer.applyCss();
        previewLayer.layout();
        double width = preview.getWidth() > 0 ? preview.getWidth() : 480;
        double height = preview.getHeight() > 0 ? preview.getHeight() : 330;
        for (NavigationEdge edge : graph.edges()) {
            NavigationNode from = findNode(edge.fromNodeId());
            NavigationNode to = findNode(edge.toNodeId());
            if (from == null || to == null || from.floor() != selectedFloor || to.floor() != selectedFloor) continue;
            Line line = new Line(from.x() * width, from.y() * height, to.x() * width, to.y() * height);
            line.setStroke(edge.verified() ? Color.web("#087443") : Color.web("#d18a00"));
            line.setStrokeWidth(3);
            previewLayer.getChildren().add(line);
        }
        for (NavigationNode node : graph.nodes()) {
            if (node.floor() != selectedFloor) continue;
            Circle marker = new Circle(node.x() * width, node.y() * height, 7,
                    node.verified() ? Color.web("#087443") : Color.web("#d18a00"));
            marker.setStroke(Color.WHITE);
            marker.setStrokeWidth(2);
            Label label = new Label(node.label());
            label.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #142b59; "
                    + "-fx-background-color: rgba(255,255,255,.82); -fx-padding: 1 3 1 3;");
            label.setLayoutX(node.x() * width + 8);
            label.setLayoutY(node.y() * height - 8);
            previewLayer.getChildren().addAll(marker, label);
        }
    }

    private String nodeName(String id) {
        NavigationNode node = findNode(id);
        return node == null ? "Missing node" : node.label();
    }

    private NavigationNode findNode(String id) {
        return graph.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    private Room roomById(int id) {
        return rooms.stream().filter(room -> room.id() == id).findFirst().orElse(null);
    }

    private void selectNode(String id) {
        nodesList.getItems().stream().filter(node -> node.id().equals(id)).findFirst().ifPresent(node -> {
            nodesList.getSelectionModel().select(node);
            nodesList.scrollTo(node);
        });
    }
}
