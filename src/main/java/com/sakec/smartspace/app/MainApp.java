package com.sakec.smartspace.app;

import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Log;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        // Optional: drop Inter-Regular.ttf / Inter-Bold.ttf into src/main/resources/fonts to use Inter.
        for (String f : new String[]{"/fonts/Inter-Regular.ttf", "/fonts/Inter-Medium.ttf", "/fonts/Inter-SemiBold.ttf", "/fonts/Inter-Bold.ttf"}) {
            var in = MainApp.class.getResourceAsStream(f);
            if (in != null) Font.loadFont(in, 13);
        }
        SceneManager.init(stage);
        try {
            Db.initSchema();
            Bootstrap.handle(getParameters().getRaw().toArray(new String[0]));
            SceneManager.showLogin();
        } catch (RuntimeException e) {
            Log.error("Startup failed", e);
            Label l = new Label("SmartSpace can't open its database.\nCheck db.url in smartspace.properties and that the folder is writable.");
            l.setWrapText(true);
            l.setStyle("-fx-padding: 40; -fx-font-size: 15px;");
            stage.setScene(new Scene(new StackPane(l), 640, 220));
            stage.show();
        }
    }

    @Override
    public void stop() { Async.shutdown(); }

    public static void main(String[] args) { launch(args); }
}
