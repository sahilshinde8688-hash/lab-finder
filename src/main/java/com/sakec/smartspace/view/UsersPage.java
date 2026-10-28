package com.sakec.smartspace.view;

import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.ui.Forms;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.ui.StatusBadge;
import com.sakec.smartspace.ui.Tables;
import com.sakec.smartspace.ui.Toast;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Errors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Coordinators create student/teacher accounts and reset passwords. Self sign-up is intentionally not offered. */
public class UsersPage extends Page {
    private final AuthService auth = new AuthService();
    private final VBox tableHost = new VBox();

    public UsersPage() {
        Button add = Forms.withIcon(Forms.primary("Add user"), "plus", "#FFFFFF");
        add.setOnAction(e -> addDialog());
        body.getChildren().addAll(Forms.pageHeader("Users", "Create accounts for students and teachers. People can't choose their own role.", add),
                Forms.card(null, tableHost));
    }

    @Override public String title() { return "Users"; }

    @Override public void show() {
        load(auth::listUsers, rows -> tableHost.getChildren().setAll(Tables.table(rows, "No users", "Add the first user.",
                Tables.text("Name", User::fullName, 160),
                Tables.text("Email", User::email, 230),
                Tables.text("Role", u -> u.role().label(), 150),
                Tables.text("Department", u -> u.department() == null ? "" : u.department(), 160),
                Tables.node("Status", u -> StatusBadge.make(u.active() ? "Active" : "Disabled", u.active() ? "badge-available" : "badge-unavailable"), 110),
                Tables.node("", this::actions, 300))));
    }

    private Node actions(User u) {
        HBox row = new HBox(8);
        Button reset = Forms.secondary("Reset password");
        reset.getStyleClass().add("btn-sm");
        reset.setOnAction(e -> Modal.confirm("Reset password for " + u.fullName() + "?",
                "A new temporary password will be generated. They must change it at their next sign-in.", "Reset", false,
                () -> Async.run(() -> auth.resetPassword(u.id()), pw -> showTemporary(u.fullName(), pw),
                        ex -> Toast.error(Errors.message(ex)))));
        row.getChildren().add(reset);
        if (u.id() != Session.user().id()) {
            Button toggle = u.active() ? Forms.danger("Disable") : Forms.primary("Enable");
            toggle.getStyleClass().add("btn-sm");
            toggle.setOnAction(e -> act(() -> { auth.setActive(u.id(), !u.active()); return u.active() ? "Account disabled." : "Account enabled."; }));
            row.getChildren().add(toggle);

            Button delete = Forms.danger("Delete");
            delete.getStyleClass().add("btn-sm");
            delete.setOnAction(e -> Modal.confirm("Delete " + u.fullName() + "?",
                    "Are you sure you want to completely delete " + u.fullName() + " (" + u.email() + ") from the database?\nThis will permanently remove the user and their associated data.",
                    "Delete", true,
                    () -> act(() -> { auth.deleteUser(u.id()); return "User deleted successfully."; })));
            row.getChildren().add(delete);
        }
        return row;
    }

    private void addDialog() {
        TextField name = new TextField();
        name.setPromptText("Full name");
        TextField email = new TextField();
        email.setPromptText("name@college domain");
        ComboBox<Role> role = new ComboBox<>();
        role.getItems().addAll(Role.STUDENT, Role.TEACHER);
        role.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Role r) { return r == null ? "" : r.label(); }
            @Override public Role fromString(String s) { return null; }
        });
        role.setValue(Role.STUDENT);
        TextField dept = new TextField();
        dept.setPromptText("Department (optional)");
        Label error = Forms.errorLabel();
        VBox form = new VBox(14, Forms.field("Full name", name), Forms.field("Institutional email", email),
                new HBox(12, Forms.field("Role", role), Forms.field("Department", dept)), error);
        Modal m = new Modal("Add user", form).width(500);
        Button cancel = Forms.secondary("Cancel");
        Button create = Forms.primary("Create account");
        cancel.setOnAction(e -> m.close());
        create.setOnAction(e -> {
            Forms.showError(error, null);
            create.setDisable(true);
            Async.run(() -> auth.createUser(name.getText(), email.getText(), role.getValue(), dept.getText()),
                    pw -> { m.close(); showTemporary(name.getText(), pw); show(); },
                    ex -> { create.setDisable(false); Forms.showError(error, Errors.message(ex)); });
        });
        m.actions(cancel, create).show();
    }

    private void showTemporary(String who, String password) {
        TextField box = new TextField(password);
        box.setEditable(false);
        box.getStyleClass().add("mono");
        Button copy = Forms.secondary("Copy");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(password);
            Clipboard.getSystemClipboard().setContent(c);
            Toast.info("Copied to clipboard.");
        });
        HBox row = new HBox(8, box, copy);
        HBox.setHgrow(box, javafx.scene.layout.Priority.ALWAYS);
        VBox v = new VBox(12, Forms.muted("Share this temporary password with " + who + " privately. It is shown only once, "
                + "and they must choose their own password at first sign-in."), row);
        Modal m = new Modal("Temporary password", v).width(480).dismissable(false);
        Button done = Forms.primary("Done");
        done.setOnAction(e -> m.close());
        m.actions(done).show();
    }
}
