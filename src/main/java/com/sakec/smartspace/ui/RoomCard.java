package com.sakec.smartspace.ui;

import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Card showing one room, its live status and action buttons. */
public class RoomCard extends VBox {
    public RoomCard(RoomAvailability a, List<? extends Node> actions) {
        super(10);
        Room r = a.room();
        getStyleClass().addAll("card", "room-card");
        setPrefWidth(300);
        setMinWidth(220);

        Label name = new Label(r.name());
        name.getStyleClass().add("room-name");
        name.setMinWidth(0);
        name.setWrapText(true);
        Label meta = new Label("Floor " + r.floor() + " · " + r.type().label() + (r.sample() ? " · sample" : ""));
        meta.getStyleClass().add("muted");
        meta.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox nameBox = new VBox(2, name, meta);
        nameBox.setMinWidth(0);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        HBox top = new HBox(8, nameBox, spacer, StatusBadge.of(a.status()));
        top.setAlignment(Pos.TOP_LEFT);

        Label detail = new Label(a.detail());
        detail.getStyleClass().add("room-detail");
        detail.setWrapText(true);

        FlowPane chips = new FlowPane(6, 6);
        chips.getChildren().add(chip(r.capacity() + " seats"));
        List<String> facilities = r.facilityList();
        for (int i = 0; i < Math.min(3, facilities.size()); i++) chips.getChildren().add(chip(facilities.get(i)));
        if (facilities.size() > 3) chips.getChildren().add(chip("+" + (facilities.size() - 3)));

        getChildren().addAll(top, detail, chips);
        if (!actions.isEmpty()) {
            HBox row = new HBox(8);
            row.getChildren().addAll(actions);
            getChildren().add(row);
        }
        Anim.hoverLift(this);
    }

    private static Label chip(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("chip");
        return l;
    }
}
