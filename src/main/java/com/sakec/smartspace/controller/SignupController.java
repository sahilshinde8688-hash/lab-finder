package com.sakec.smartspace.controller;

import com.sakec.smartspace.app.SceneManager;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.service.AuthService.RegistrationResult;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.Brand;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Icons;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Errors;
import com.sakec.smartspace.util.Validators;
import java.net.URL;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

/**
 * Controller for the student self-registration screen (Signup.fxml).
 *
 * <p>Security notes:
 * <ul>
 *   <li>Email is normalised (trimmed + lower-cased) before any validation or storage.</li>
 *   <li>Only the {@code sakec.onmicrosoft.com} domain is accepted (configurable via
 *       {@code auth.studentDomains} in smartspace.properties).</li>
 *   <li>Password is never echoed in logs or error messages.</li>
 *   <li>The form is disabled while a registration request is in flight to prevent duplicate
 *       submissions.</li>
 *   <li>Role is always {@code STUDENT}; the service layer enforces this.</li>
 * </ul>
 */
public class SignupController {

    private static final double NARROW_LAYOUT_WIDTH = 900;

    private final AuthService auth = new AuthService();

    /** True when a registration request is in flight. */
    private boolean busy;

    /** Stores the registered email so the resend button knows which address to use. */
    private String registeredEmail = "";

    /** True when the confirm-password field is currently visible (plain-text). */
    private boolean showingConfirm;

    /** True when the password field is currently visible (plain-text). */
    private boolean showingPassword;

    // ---- FXML fields -----------------------------------------------------------------------

    @FXML private StackPane root;
    @FXML private StackPane artPane;
    @FXML private HBox brandBox;

    @FXML private Label nameLabel;
    @FXML private TextField nameField;
    @FXML private Label nameError;

    @FXML private Label emailLabel;
    @FXML private TextField emailField;
    @FXML private Label emailError;

    @FXML private Label branchLabel;
    @FXML private ComboBox<String> branchCombo;
    @FXML private Label branchError;

    @FXML private Label passwordLabel;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisible;
    @FXML private Button toggleButton;
    @FXML private Label passwordError;

    @FXML private Label confirmLabel;
    @FXML private PasswordField confirmField;
    @FXML private TextField confirmVisible;
    @FXML private Button toggleConfirmButton;
    @FXML private Label confirmError;

    @FXML private Label formError;

    @FXML private VBox successBox;
    @FXML private Label successLabel;
    @FXML private Button goSignInButton;
    @FXML private Button resendFromSuccessButton;
    @FXML private Label resendSuccessStatus;

    @FXML private StackPane actionRow;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator spinner;

    // ---- Initialise ------------------------------------------------------------------------

    @FXML
    private void initialize() {
        Brand.fill(brandBox, false);

        // Password toggle icons
        toggleButton.setGraphic(Icons.of("eye", 18));
        toggleButton.setAccessibleText("Show password");
        toggleConfirmButton.setGraphic(Icons.of("eye", 18));
        toggleConfirmButton.setAccessibleText("Show confirm password");

        // Accessibility – associate labels with their inputs
        nameLabel.setLabelFor(nameField);
        emailLabel.setLabelFor(emailField);
        branchLabel.setLabelFor(branchCombo);
        passwordLabel.setLabelFor(passwordField);
        confirmLabel.setLabelFor(confirmField);

        // Keep visible text-fields in sync with the password fields
        passwordVisible.textProperty().bindBidirectional(passwordField.textProperty());
        confirmVisible.textProperty().bindBidirectional(confirmField.textProperty());

        // Populate the branch dropdown from configuration
        List<String> branches = Validators.branches();
        branchCombo.getItems().setAll(branches);
        branchCombo.setMaxWidth(Double.MAX_VALUE);

        // Art pane illustration
        buildArt();

        // Responsive layout
        root.widthProperty().addListener((obs, oldW, newW) ->
                updateResponsiveLayout(newW.doubleValue()));
        updateResponsiveLayout(root.getWidth());

        // Fade in
        Anim.fadeIn(root.lookup("#formBox") != null ? root.lookup("#formBox") : root);

        // Focus the name field on load
        javafx.application.Platform.runLater(nameField::requestFocus);
    }

    // ---- Password visibility toggles -------------------------------------------------------

    @FXML
    private void togglePassword() {
        TextField current = showingPassword ? passwordVisible : passwordField;
        int caret = current.getCaretPosition();
        showingPassword = !showingPassword;
        passwordVisible.setVisible(showingPassword);
        passwordVisible.setManaged(showingPassword);
        passwordField.setVisible(!showingPassword);
        passwordField.setManaged(!showingPassword);
        toggleButton.setGraphic(Icons.of(showingPassword ? "eye-off" : "eye", 18));
        toggleButton.setAccessibleText(showingPassword ? "Hide password" : "Show password");
        passwordLabel.setLabelFor(showingPassword ? passwordVisible : passwordField);
        TextField active = showingPassword ? passwordVisible : passwordField;
        active.requestFocus();
        active.positionCaret(Math.min(caret, active.getLength()));
    }

    @FXML
    private void toggleConfirm() {
        TextField current = showingConfirm ? confirmVisible : confirmField;
        int caret = current.getCaretPosition();
        showingConfirm = !showingConfirm;
        confirmVisible.setVisible(showingConfirm);
        confirmVisible.setManaged(showingConfirm);
        confirmField.setVisible(!showingConfirm);
        confirmField.setManaged(!showingConfirm);
        toggleConfirmButton.setGraphic(Icons.of(showingConfirm ? "eye-off" : "eye", 18));
        toggleConfirmButton.setAccessibleText(showingConfirm ? "Hide password" : "Show password");
        confirmLabel.setLabelFor(showingConfirm ? confirmVisible : confirmField);
        TextField active = showingConfirm ? confirmVisible : confirmField;
        active.requestFocus();
        active.positionCaret(Math.min(caret, active.getLength()));
    }

    // ---- Navigation ------------------------------------------------------------------------

    @FXML
    private void goToLogin() {
        SceneManager.showLogin();
    }

    /** Resends the confirmation email from the post-registration success screen. */
    @FXML
    private void resendFromSuccess() {
        if (registeredEmail.isBlank()) return;
        resendFromSuccessButton.setDisable(true);
        resendFromSuccessButton.setText("Sending…");
        Forms.showError(resendSuccessStatus, null);
        Async.run(() -> { auth.resendVerificationEmail(registeredEmail); return null; },
                ignored -> {
                    resendFromSuccessButton.setText("Sent!");
                    PauseTransition p = new PauseTransition(Duration.seconds(30));
                    p.setOnFinished(e -> {
                        resendFromSuccessButton.setText("Resend confirmation email");
                        resendFromSuccessButton.setDisable(false);
                    });
                    p.play();
                },
                ex -> {
                    resendFromSuccessButton.setText("Resend confirmation email");
                    resendFromSuccessButton.setDisable(false);
                    Forms.showError(resendSuccessStatus, Errors.message(ex));
                });
    }

    // ---- Registration ----------------------------------------------------------------------

    @FXML
    private void register() {
        if (busy) return;

        // Clear all previous errors
        clearErrors();

        // ---- Inline client-side validation (fast feedback before hitting the service)
        String name  = nameField.getText().trim();
        String email = emailField.getText().trim().toLowerCase();
        String branch = branchCombo.getValue();
        String password = passwordField.getText();        // never trim passwords
        String confirm  = confirmField.getText();

        boolean valid = true;

        if (name.isBlank()) {
            Forms.showError(nameError, "Enter your full name.");
            valid = false;
        } else if (name.length() > 80) {
            Forms.showError(nameError, "Name is too long (max 80 characters).");
            valid = false;
        }

        String emailErr = Validators.checkStudentEmail(email);
        if (emailErr != null) {
            Forms.showError(emailError, emailErr);
            valid = false;
        }

        if (branch == null || branch.isBlank()) {
            Forms.showError(branchError, "Select your branch.");
            valid = false;
        }

        String pwErr = Validators.checkPassword(password);
        if (pwErr != null) {
            Forms.showError(passwordError, pwErr);
            valid = false;
        } else if (!password.equals(confirm)) {
            Forms.showError(confirmError, "Passwords do not match.");
            valid = false;
        }

        if (!valid) return;

        // ---- Submit asynchronously so the UI stays responsive
        setBusy(true);
        registeredEmail = email; // save for resend button
        Async.run(
                () -> auth.selfRegister(name, email, password, confirm, branch),
                result -> {
                    setBusy(false);
                    showSuccess(result);
                },
                ex -> {
                    setBusy(false);
                    Forms.showError(formError, Errors.message(ex));
                }
        );
    }

    // ---- Private helpers -------------------------------------------------------------------

    private void clearErrors() {
        Forms.showError(nameError,     null);
        Forms.showError(emailError,    null);
        Forms.showError(branchError,   null);
        Forms.showError(passwordError, null);
        Forms.showError(confirmError,  null);
        Forms.showError(formError,     null);
    }

    /**
     * Locks/unlocks all interactive controls while registration is in flight.
     * Prevents double-submission and provides clear loading feedback.
     */
    private void setBusy(boolean busy) {
        this.busy = busy;
        registerButton.setDisable(busy);
        registerButton.setText(busy ? "Creating account…" : "Create account");
        registerButton.setAccessibleText(busy ? "Creating account" : "Create account");
        nameField.setDisable(busy);
        emailField.setDisable(busy);
        branchCombo.setDisable(busy);
        passwordField.setDisable(busy);
        passwordVisible.setDisable(busy);
        confirmField.setDisable(busy);
        confirmVisible.setDisable(busy);
        toggleButton.setDisable(busy);
        toggleConfirmButton.setDisable(busy);
        spinner.setVisible(busy);
        spinner.setManaged(busy);
    }

    /** Hides the registration form and shows the success banner. */
    private void showSuccess(RegistrationResult result) {
        // Update the success message — always tell students to verify their email
        String msg = "Account created! Check your inbox at " + emailField.getText().trim().toLowerCase()
                + " for a confirmation link. Click it, then come back and sign in.";
        successLabel.setText(msg);

        // Hide the register button row
        actionRow.setVisible(false);
        actionRow.setManaged(false);

        // Disable form fields so the user can't accidentally resubmit
        nameField.setDisable(true);
        emailField.setDisable(true);
        branchCombo.setDisable(true);
        passwordField.setDisable(true);
        passwordVisible.setDisable(true);
        confirmField.setDisable(true);
        confirmVisible.setDisable(true);
        toggleButton.setDisable(true);
        toggleConfirmButton.setDisable(true);

        // Show success banner
        successBox.setVisible(true);
        successBox.setManaged(true);
        Anim.fadeIn(successBox);

        // Put focus on the "Go to Sign In" button for keyboard navigation
        javafx.application.Platform.runLater(goSignInButton::requestFocus);
    }

    private void buildArt() {
        URL imageUrl = getClass().getResource("/images/login.png");
        if (imageUrl == null) return;   // illustration is optional for the sign-up screen
        Image image = new Image(imageUrl.toExternalForm(), false);
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(artPane.widthProperty());
        imageView.fitHeightProperty().bind(artPane.heightProperty());
        artPane.getChildren().add(imageView);
    }

    private void updateResponsiveLayout(double width) {
        if (width <= 0) return;
        boolean narrow = width < NARROW_LAYOUT_WIDTH;
        artPane.setVisible(!narrow);
        artPane.setManaged(!narrow);

        // Locate the ScrollPane (second child of HBox in Signup.fxml)
        if (root.getChildren().isEmpty()) return;
        var hbox = (HBox) root.getChildren().get(0);
        if (hbox.getChildren().size() < 2) return;
        var scroll = hbox.getChildren().get(1);
        scroll.maxWidth(narrow ? Double.MAX_VALUE : 620);
        ((javafx.scene.layout.Region) scroll).setPrefWidth(narrow ? width : Math.min(width * 0.45, 620));

        // Padding via the formSide StackPane
        var formSideNode = ((javafx.scene.control.ScrollPane) scroll).getContent();
        if (formSideNode instanceof StackPane sp) {
            sp.setPadding(narrow ? new Insets(24) : new Insets(32, 40, 32, 40));
        }
    }
}
