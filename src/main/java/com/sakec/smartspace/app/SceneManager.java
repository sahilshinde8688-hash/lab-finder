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
        s.setMinWidth(1020);
        s.setMinHeight(680);
        s.setWidth(1280);
        s.setHeight(800);
    }

    public static void showLogin() { swap("/fxml/Login.fxml"); }

    public static void showMain() { swap("/fxml/Main.fxml"); }

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
