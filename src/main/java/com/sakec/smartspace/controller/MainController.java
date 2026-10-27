package com.sakec.smartspace.controller;

import com.sakec.smartspace.app.Events;
import com.sakec.smartspace.app.SceneManager;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.service.NotificationService;
import com.sakec.smartspace.ui.Anim;
import com.sakec.smartspace.ui.Brand;
import com.sakec.smartspace.ui.Icons;
import com.sakec.smartspace.ui.Modal;
import com.sakec.smartspace.util.Async;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.view.AnalyticsPage;
import com.sakec.smartspace.view.BookingsPage;
import com.sakec.smartspace.view.DashboardPage;
import com.sakec.smartspace.view.Dialogs;
import com.sakec.smartspace.view.FloorMapPage;
import com.sakec.smartspace.view.MaintenancePage;
import com.sakec.smartspace.view.Navigator;
import com.sakec.smartspace.view.NotificationsPage;
import com.sakec.smartspace.view.Page;
import com.sakec.smartspace.view.RoomsPage;
import com.sakec.smartspace.view.TimetablePage;
import com.sakec.smartspace.view.UsersPage;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** The shell: sidebar, header and the page area. Pages are chosen by role. */
public class MainController implements Navigator {
    private final User me = Session.user();
    private final NotificationService notifications = new NotificationService();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private Timeline poller;

    @FXML private StackPane root;
    @FXML private HBox brandBox;
    @FXML private VBox navBox;
    @FXML private Label avatarLabel, nameLabel, roleLabel, pageTitle, dateLabel, badgeLabel;
    @FXML private Button bellButton;
    @FXML private MenuButton profileMenu;
    @FXML private StackPane contentHost;

    @FXML
    private void initialize() {
        com.sakec.smartspace.ui.Overlay.setHost(root);
        Brand.fill(brandBox, true);
        avatarLabel.setText(me.initials());
        nameLabel.setText(me.fullName());
        roleLabel.setText(me.role().label());
        dateLabel.setText(LocalDate.now().format(Dt.LONG_DATE));
        bellButton.setGraphic(Icons.of("bell", 20, "#0B1F4B"));
        profileMenu.setText(me.firstName());
        profileMenu.setGraphic(Icons.of("user", 16, "#0B1F4B"));

        addNav("dashboard", "Dashboard", "home");
        addNav("map", "Floor map", "map");
        switch (me.role()) {
            case STUDENT -> {
                addNav("rooms", "Find rooms", "search");
                addNav("bookings", "My bookings", "calendar");
                addNav("maintenance", "My reports", "flag");
            }
            case TEACHER -> {
                addNav("rooms", "Find rooms", "search");
                addNav("timetable", "Timetable", "clock");
                addNav("bookings", "My bookings", "calendar");
                addNav("maintenance", "My reports", "flag");
            }
            case COORDINATOR -> {
                addNav("rooms", "Rooms", "grid");
                addNav("timetable", "Timetable", "clock");
                addNav("requests", "Booking requests", "inbox");
                addNav("maintenance", "Maintenance", "tool");
                addNav("analytics", "Analytics", "chart");
                addNav("users", "Users", "users");
            }
        }
        addNav("notifications", "Notifications", "bell");
        Button out = new Button("Sign out");
        out.setGraphic(Icons.of("logout", 18, "#B8C4E0"));
        out.getStyleClass().add("nav-item");
        out.setMaxWidth(Double.MAX_VALUE);
        out.setAlignment(Pos.CENTER_LEFT);
        out.setGraphicTextGap(12);
        out.setOnAction(e -> signOut());
        navBox.getChildren().add(out);

        Events.onNotificationsChanged(this::refreshBadge);
        refreshBadge();
        poller = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshBadge()));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();

        go("dashboard");
        if (me.mustChangePassword()) {
            Dialogs.changePassword(true, () -> { }, this::doSignOut);
        }
    }

    private void addNav(String key, String label, String icon) {
        Button b = new Button(label);
        b.setGraphic(Icons.of(icon, 18, "#B8C4E0"));
        b.setGraphicTextGap(12);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setOnAction(e -> go(key));
        navButtons.put(key, b);
        navBox.getChildren().add(b);
    }

    @Override
    public void go(String key) {
        Page page = create(key);
        if (page == null) return;
        navButtons.forEach((k, b) -> {
            boolean active = k.equals(key) || ("bookings".equals(key) && "requests".equals(k));
            b.getStyleClass().remove("nav-item-active");
            if (active) b.getStyleClass().add("nav-item-active");
        });
        pageTitle.setText(page.title());
        contentHost.getChildren().setAll(page.root());
        Anim.fadeIn(page.root());
        page.show();
    }

    private Page create(String key) {
        boolean coordinator = me.role() == Role.COORDINATOR;
        return switch (key) {
            case "dashboard" -> new DashboardPage(this);
            case "map" -> new FloorMapPage(me.role() == Role.COORDINATOR, me.role() != Role.STUDENT, this);
            case "rooms" -> new RoomsPage(coordinator);
            case "timetable" -> me.role() == Role.STUDENT ? null : new TimetablePage();
            case "bookings", "requests" -> coordinator ? new BookingsPage(true) : new BookingsPage(false);
            case "maintenance" -> new MaintenancePage();
            case "analytics" -> coordinator ? new AnalyticsPage() : null;
            case "users" -> coordinator ? new UsersPage() : null;
            case "notifications" -> new NotificationsPage();
            default -> null;
        };
    }

    private void refreshBadge() {
        if (!Session.isSignedIn()) return;
        Async.run(notifications::unreadCount, n -> {
            badgeLabel.setText(n > 99 ? "99+" : String.valueOf(n));
            badgeLabel.setVisible(n > 0);
        }, ex -> { });
    }

    @FXML private void openNotifications() { go("notifications"); }

    @FXML private void changePassword() { Dialogs.changePassword(false, () -> { }, () -> { }); }

    @FXML
    private void signOut() {
        Modal.confirm("Sign out?", "You'll need to sign in again to continue.", "Sign out", false, this::doSignOut);
    }

    private void doSignOut() {
        if (poller != null) poller.stop();
        Events.onNotificationsChanged(null);
        new AuthService().logout();
        SceneManager.showLogin();
    }
}
