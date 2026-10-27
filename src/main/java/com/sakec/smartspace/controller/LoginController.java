package com.sakec.smartspace.controller;

import com.sakec.smartspace.app.SceneManager;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.Brand;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Icons;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.util.Validators;
import java.util.prefs.Preferences;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LoginController {
    private static final String PREF_EMAIL = "rememberedEmail";
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private final AuthService auth = new AuthService();
    private boolean showing;

    @FXML private StackPane root;
    @FXML private StackPane artPane;
    @FXML private VBox formBox;
    @FXML private HBox brandBox;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisible;
    @FXML private Button toggleButton;
    @FXML private CheckBox rememberCheck;
    @FXML private Button signInButton;
    @FXML private ProgressIndicator spinner;
    @FXML private Label emailError;
    @FXML private Label formError;

    @FXML
    private void initialize() {
        Brand.fill(brandBox, false);
        toggleButton.setGraphic(Icons.of("eye", 18));
        passwordVisible.textProperty().bindBidirectional(passwordField.textProperty());
        String saved = prefs.get(PREF_EMAIL, "");
        emailField.setText(saved);
        rememberCheck.setSelected(!saved.isEmpty());
        buildArt();
        Anim.fadeIn(formBox);
        javafx.application.Platform.runLater(() -> (saved.isEmpty() ? emailField : passwordField).requestFocus());
    }

    @FXML
    private void togglePassword() {
        showing = !showing;
        passwordVisible.setVisible(showing);
        passwordVisible.setManaged(showing);
        passwordField.setVisible(!showing);
        passwordField.setManaged(!showing);
        toggleButton.setGraphic(Icons.of(showing ? "eye-off" : "eye", 18));
    }

    @FXML
    private void signIn() {
        Forms.showError(emailError, null);
        Forms.showError(formError, null);
        String email = emailField.getText();
        String problem = Validators.checkEmail(email);
        if (problem != null) { Forms.showError(emailError, problem); return; }
        String password = passwordField.getText();
        if (password.isEmpty()) { Forms.showError(formError, "Enter your password."); return; }

        setBusy(true);
        Async.run(() -> auth.login(email, password), user -> {
            if (rememberCheck.isSelected()) prefs.put(PREF_EMAIL, email.trim()); else prefs.remove(PREF_EMAIL);
            passwordField.clear();
            SceneManager.showMain();
        }, ex -> {
            setBusy(false);
            passwordField.clear();
            Forms.showError(formError, Errors.message(ex));
        });
    }

    private void setBusy(boolean busy) {
        signInButton.setDisable(busy);
        signInButton.setText(busy ? "Signing in…" : "Sign in");
        spinner.setVisible(busy);
        spinner.setManaged(busy);
    }

    private void buildArt() {
        Image image = new Image(getClass().getResource("/images/login.png").toExternalForm());
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(artPane.widthProperty());
        imageView.fitHeightProperty().bind(artPane.heightProperty());
        artPane.getChildren().add(imageView);
    }
}
