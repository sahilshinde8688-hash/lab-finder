package com.sakec.smartspace.controller;

import com.sakec.smartspace.app.SceneManager;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.service.AuthService.EmailNotVerifiedException;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.Brand;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Icons;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.util.Log;
import com.sakec.smartspace.util.Validators;
import java.net.URL;
import java.util.prefs.Preferences;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
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
import javafx.util.Duration;

public class LoginController {
    private static final String PREF_EMAIL = "rememberedEmail";
    private static final double NARROW_LAYOUT_WIDTH = 900;

    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private final AuthService auth = new AuthService();

    private boolean showing;
    private boolean busy;

    /** Tracks the last email used so "Resend email" knows which address to use. */
    private String lastAttemptedEmail = "";

    // ---- FXML -------------------------------------------------------------------

    @FXML private StackPane root;
    @FXML private StackPane artPane;
    @FXML private StackPane formSide;
    @FXML private VBox formBox;
    @FXML private HBox brandBox;
    @FXML private Label emailLabel;
    @FXML private TextField emailField;
    @FXML private Label passwordLabel;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisible;
    @FXML private Button toggleButton;
    @FXML private CheckBox rememberCheck;
    @FXML private Button signInButton;
    @FXML private ProgressIndicator spinner;
    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label formError;

    /** Banner shown when email is not yet verified. Hidden by default. */
    @FXML private VBox verifyBanner;
    /** "Resend verification email" button inside the verify banner. */
    @FXML private Button resendButton;
    /** Status label inside the verify banner (e.g. "Email sent!"). */
    @FXML private Label resendStatus;

    // ---- Init -------------------------------------------------------------------

    @FXML
    private void initialize() {
        Brand.fill(brandBox, false);
        toggleButton.setGraphic(Icons.of("eye", 18));
        emailLabel.setLabelFor(emailField);
        passwordLabel.setLabelFor(passwordField);
        passwordVisible.textProperty().bindBidirectional(passwordField.textProperty());
        String saved = prefs.get(PREF_EMAIL, "");
        emailField.setText(saved);
        rememberCheck.setSelected(!saved.isEmpty());
        buildArt();
        root.widthProperty().addListener((observable, oldWidth, newWidth) ->
                updateResponsiveLayout(newWidth.doubleValue()));
        root.heightProperty().addListener((observable, oldHeight, newHeight) ->
                formBox.setSpacing(newHeight.doubleValue() < 700 ? 12 : 16));
        updateResponsiveLayout(root.getWidth());
        Anim.fadeIn(formBox);
        javafx.application.Platform.runLater(() ->
                (saved.isEmpty() ? emailField : passwordField).requestFocus());
    }

    // ---- Handlers ---------------------------------------------------------------

    @FXML
    private void togglePassword() {
        TextField currentField = showing ? passwordVisible : passwordField;
        int caretPosition = currentField.getCaretPosition();
        showing = !showing;
        passwordVisible.setVisible(showing);
        passwordVisible.setManaged(showing);
        passwordField.setVisible(!showing);
        passwordField.setManaged(!showing);
        toggleButton.setGraphic(Icons.of(showing ? "eye-off" : "eye", 18));
        toggleButton.setAccessibleText(showing ? "Hide password" : "Show password");
        TextField activeField = showing ? passwordVisible : passwordField;
        passwordLabel.setLabelFor(activeField);
        activeField.requestFocus();
        activeField.positionCaret(Math.min(caretPosition, activeField.getLength()));
    }

    @FXML
    private void goToSignup() {
        SceneManager.showSignup();
    }

    @FXML
    private void signIn() {
        if (busy) return;
        hideVerifyBanner();
        Forms.showError(emailError, null);
        Forms.showError(passwordError, null);
        Forms.showError(formError, null);

        String email = emailField.getText().trim();
        String problem = Validators.checkEmail(email);
        if (problem != null) { Forms.showError(emailError, problem); return; }
        String password = passwordField.getText();
        if (password.isEmpty()) { Forms.showError(passwordError, "Enter your password."); return; }

        lastAttemptedEmail = email.toLowerCase();
        setBusy(true);

        Async.run(() -> auth.login(email, password), user -> {
            try {
                if (rememberCheck.isSelected()) prefs.put(PREF_EMAIL, email.trim());
                else prefs.remove(PREF_EMAIL);
                passwordField.clear();
                SceneManager.showMain();
            } catch (Throwable t) {
                setBusy(false);
                Log.error("Failed to load main screen after login", t);
                Forms.showError(formError, "Failed to load dashboard: " + Errors.message(t));
            }
        }, ex -> {
            setBusy(false);
            passwordField.clear();
            if (ex instanceof EmailNotVerifiedException) {
                // Show the dedicated verification banner instead of the generic error box
                showVerifyBanner(ex.getMessage());
            } else {
                Forms.showError(formError, Errors.message(ex));
            }
        });
    }

    /** Resends the Supabase confirmation email for the last attempted email address. */
    @FXML
    private void resendVerificationEmail() {
        if (lastAttemptedEmail.isBlank()) return;
        resendButton.setDisable(true);
        resendButton.setText("Sending…");
        Forms.showError(resendStatus, null);

        Async.run(() -> {
            auth.resendVerificationEmail(lastAttemptedEmail);
            return null;
        }, ignored -> {
            resendButton.setText("Email sent!");
            Forms.showError(resendStatus, null);
            // Re-enable the button after 30 s to allow retrying
            PauseTransition pause = new PauseTransition(Duration.seconds(30));
            pause.setOnFinished(e -> {
                resendButton.setText("Resend verification email");
                resendButton.setDisable(false);
            });
            pause.play();
        }, ex -> {
            resendButton.setText("Resend verification email");
            resendButton.setDisable(false);
            Forms.showError(resendStatus, Errors.message(ex));
        });
    }

    // ---- Private helpers --------------------------------------------------------

    private void showVerifyBanner(String detail) {
        verifyBanner.setVisible(true);
        verifyBanner.setManaged(true);
        Anim.fadeIn(verifyBanner);
        resendButton.setDisable(false);
        resendButton.setText("Resend verification email");
        Forms.showError(resendStatus, null);
    }

    private void hideVerifyBanner() {
        verifyBanner.setVisible(false);
        verifyBanner.setManaged(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        signInButton.setDisable(busy);
        signInButton.setText(busy ? "Signing in…" : "Sign in");
        signInButton.setAccessibleText(busy ? "Signing in" : "Sign in");
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        passwordVisible.setDisable(busy);
        toggleButton.setDisable(busy);
        rememberCheck.setDisable(busy);
        spinner.setVisible(busy);
        spinner.setManaged(busy);
    }

    private void updateResponsiveLayout(double width) {
        if (width <= 0) return;
        boolean narrow = width < NARROW_LAYOUT_WIDTH;
        artPane.setVisible(!narrow);
        artPane.setManaged(!narrow);
        formSide.setMaxWidth(narrow ? Double.MAX_VALUE : 600);
        formSide.setPrefWidth(narrow ? width : Math.min(width * 0.4, 600));
        formSide.setPadding(narrow ? new Insets(24) : width < 1120
                ? new Insets(24) : new Insets(32, 40, 32, 40));
    }

    private void buildArt() {
        URL imageUrl = getClass().getResource("/images/login.png");
        if (imageUrl == null) throw new IllegalStateException(
                "Missing login illustration: /images/login.png");
        Image image = new Image(imageUrl.toExternalForm(), false);
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(artPane.widthProperty());
        imageView.fitHeightProperty().bind(artPane.heightProperty());
        artPane.getChildren().add(imageView);
    }
}
