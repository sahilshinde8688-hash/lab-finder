package com.sakec.smartspace.app;

import com.sakec.smartspace.service.NotificationSound;
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
            showSupabaseSetupScreen(stage, e);
        }
    }

    private void showSupabaseSetupScreen(Stage stage, RuntimeException initialError) {
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(16);
        root.setAlignment(javafx.geometry.Pos.CENTER);
        root.setPadding(new javafx.geometry.Insets(36));
        root.setStyle("-fx-background-color: #f8fafc;");

        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(14);
        card.setMaxWidth(480);
        card.setPadding(new javafx.geometry.Insets(28));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 12, 0, 0, 4); -fx-border-color: #e2e8f0; -fx-border-radius: 12px;");

        Label title = new Label("Connect to Supabase Database");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitle = new Label("SmartSpace connects directly to your Supabase PostgreSQL cloud database.\nEnter your Supabase database password to continue.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-line-spacing: 2px;");

        Label hostLabel = new Label("Host: db.ezcojvjsshecapesrsxs.supabase.co:5432 (User: postgres)");
        hostLabel.setStyle("-fx-font-size: 12px; -fx-font-family: monospace; -fx-text-fill: #334155; -fx-background-color: #f1f5f9; -fx-padding: 6 10; -fx-background-radius: 6px;");

        javafx.scene.control.PasswordField passField = new javafx.scene.control.PasswordField();
        passField.setPromptText("Enter Supabase Database Password");
        passField.setStyle("-fx-padding: 10; -fx-font-size: 14px; -fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px;");

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ef4444;");

        String initialMsg = initialError.getCause() != null ? initialError.getCause().getMessage() : initialError.getMessage();
        if (initialMsg != null && !initialMsg.isBlank() && !initialMsg.contains("YOUR_SUPABASE_DB_PASSWORD")) {
            statusLabel.setText("Last status: " + initialMsg);
        }

        javafx.scene.control.Button connectBtn = new javafx.scene.control.Button("Connect & Launch SmartSpace");
        connectBtn.setMaxWidth(Double.MAX_VALUE);
        connectBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10; -fx-background-radius: 6px; -fx-cursor: hand;");

        javafx.scene.control.Hyperlink helpLink = new javafx.scene.control.Hyperlink("Forgot password? Reset in Supabase Dashboard → Settings → Database");
        helpLink.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        connectBtn.setOnAction(evt -> {
            String pwd = passField.getText();
            if (pwd == null || pwd.isBlank()) {
                statusLabel.setText("Please enter your Supabase database password.");
                return;
            }
            connectBtn.setDisable(true);
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2563eb;");
            statusLabel.setText("Connecting to Supabase PostgreSQL...");

            Async.run(() -> {
                String url = com.sakec.smartspace.util.Config.get("db.url", "jdbc:postgresql://db.ezcojvjsshecapesrsxs.supabase.co:5432/postgres?sslmode=require");
                String user = com.sakec.smartspace.util.Config.get("db.user", "postgres");
                try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, user, pwd.trim())) {
                    // Update smartspace.properties
                    savePasswordToProperties(pwd.trim());
                    System.setProperty("smartspace.db.password", pwd.trim());
                    return null;
                }
            }, success -> {
                try {
                    Db.initSchema();
                    Bootstrap.handle(getParameters().getRaw().toArray(new String[0]));
                    SceneManager.showLogin();
                } catch (Exception ex) {
                    connectBtn.setDisable(false);
                    statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ef4444;");
                    statusLabel.setText("Schema init error: " + ex.getMessage());
                }
            }, err -> {
                connectBtn.setDisable(false);
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ef4444;");
                String errMsg = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                statusLabel.setText("Connection failed: " + errMsg);
            });
        });

        card.getChildren().addAll(title, subtitle, hostLabel, passField, connectBtn, statusLabel, helpLink);
        root.getChildren().add(card);

        stage.setScene(new Scene(root, 620, 480));
        stage.setTitle("SmartSpace — Database Setup");
        stage.show();
    }

    private static void savePasswordToProperties(String password) {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("smartspace.properties");
            java.util.List<String> lines = java.nio.file.Files.exists(p) ? java.nio.file.Files.readAllLines(p) : new java.util.ArrayList<>();
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("db.password=")) {
                    lines.set(i, "db.password=" + password);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add("db.password=" + password);
            }
            java.nio.file.Files.write(p, lines);
        } catch (java.io.IOException e) {
            Log.error("Could not write smartspace.properties", e);
        }
    }

    @Override
    public void stop() {
        NotificationSound.shutdown();
        Async.shutdown();
    }

    public static void main(String[] args) { launch(args); }
}
