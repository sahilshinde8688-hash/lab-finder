package com.sakec.smartspace.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared campus branding used by the application shell. */
public final class Brand {
    private Brand() { }

    public static void fill(HBox box, boolean onDark) {
        boolean sidebar = onDark;
        Image image = new Image(Brand.class.getResourceAsStream("/images/logo2.png"));
        ImageView logo = new ImageView(image);
        logo.setFitWidth(sidebar ? 56 : 84);
        logo.setFitHeight(sidebar ? 58 : 88);
        logo.setPreserveRatio(true);
        StackPane logoBadge = new StackPane(logo);
        if (sidebar) {
            logoBadge.getStyleClass().add("brand-logo-badge");
            logoBadge.setMinSize(64, 66);
            logoBadge.setPrefSize(64, 66);
        }

        Label trust = new Label("Mahavir Education Trust's");
        trust.getStyleClass().add("brand-trust");
        Label college = new Label("Shah & Anchor");
        college.getStyleClass().add("brand-college");
        Label school = new Label("Kutchi Engineering College");
        school.getStyleClass().add("brand-school");
        Label minority = new Label("Linguistic Minority - Gujarati (Jain)");
        minority.getStyleClass().add("brand-minority");
        VBox wordmark = new VBox(0, trust, college, school, minority);
        wordmark.getStyleClass().addAll(
                onDark ? "brand-wordmark-dark" : "brand-wordmark",
                sidebar ? "brand-sidebar" : "brand-login");
        wordmark.setPrefWidth(sidebar ? 126 : 280);
        wordmark.setMaxWidth(Double.MAX_VALUE);
        wordmark.setMinWidth(0);
        for (Label label : new Label[]{trust, college, school, minority}) {
            label.setMaxWidth(Double.MAX_VALUE);
            label.setWrapText(false);
        }
        HBox.setHgrow(wordmark, Priority.ALWAYS);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setSpacing(8);
        box.getChildren().setAll(logoBadge, wordmark);
    }
}
