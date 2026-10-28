package com.sakec.smartspace.app;

import com.sakec.smartspace.ui.Overlay;
import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Switches the window between the login screen and the main shell. */
public final class SceneManager {
    private static Stage stage;

    private SceneManager() { }

    public static void init(Stage s) {
        stage = s;
        s.setTitle("SmartSpace – SAKEC");
        s.setMinWidth(900);
        s.setMinHeight(600);
        s.setWidth(1280);
        s.setHeight(800);
        s.setMaximized(true);
    }

    public static void showLogin() {
        stage.setMinWidth(560);
        stage.setMinHeight(600);
        swap("/fxml/Login.fxml");
    }

    public static void showSignup() {
        stage.setMinWidth(560);
        stage.setMinHeight(680);
        swap("/fxml/Signup.fxml");
    }

    public static void showMain() {
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        swap("/fxml/Main.fxml");
    }

    private static void swap(String fxml) {
        try {
            URL url = SceneManager.class.getResource(fxml);
            Parent root = FXMLLoader.load(url);
            Overlay.setHost((StackPane) root);
            Scene scene = new Scene(root, stage.getWidth() > 0 ? stage.getWidth() : 1280, stage.getHeight() > 0 ? stage.getHeight() : 800);
            scene.getStylesheets().add(SceneManager.class.getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load " + fxml, e);
        }
    }
}
