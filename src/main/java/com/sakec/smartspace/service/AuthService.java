package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.PasswordHasher;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Config;
import com.sakec.smartspace.util.Log;
import com.sakec.smartspace.util.Validators;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class AuthService {
    private static final String GENERIC = "Incorrect email or password.";
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password-1");
    private final UserDao users = new UserDao();

    public User login(String email, String password) {
        String emailError = Validators.checkEmail(email);
        if (emailError != null) throw new AppException(emailError);
        if (password == null || password.isEmpty()) throw new AppException("Enter your password.");

        User u = users.findByEmail(email);
        if (u == null) {
            PasswordHasher.verify(password, DUMMY_HASH); // keeps timing similar for unknown accounts
            throw new AppException(GENERIC);
        }
        LocalDateTime now = LocalDateTime.now();
        if (u.lockedUntil() != null && u.lockedUntil().isAfter(now)) {
            long mins = Math.max(1, Duration.between(now, u.lockedUntil()).toMinutes() + 1);
            throw new AppException("Too many failed attempts. Try again in " + mins + " minute(s).");
        }
        if (!PasswordHasher.verify(password, u.passwordHash())) {
            int attempts = u.failedAttempts() + 1;
            LocalDateTime lock = null;
            if (attempts >= Config.getInt("auth.maxFailedAttempts", 5)) {
                lock = now.plusMinutes(Config.getInt("auth.lockMinutes", 10));
                attempts = 0;
                Log.warn("Account locked after repeated failures, userId=" + u.id());
            }
            users.recordFailure(u.id(), attempts, lock);
            throw new AppException(GENERIC);
        }
        if (!u.active()) throw new AppException("This account is disabled. Contact the timetable coordinator.");
        users.clearFailures(u.id());
        Session.start(u);
        Log.info("Sign-in ok, userId=" + u.id() + ", role=" + u.role());
        return u;
    }

    public void logout() {
        if (Session.isSignedIn()) Log.info("Sign-out, userId=" + Session.user().id());
        Session.clear();
    }

    public void changePassword(String current, String newPassword) {
        User u = Session.user();
        User fresh = users.findById(u.id());
        if (!PasswordHasher.verify(current == null ? "" : current, fresh.passwordHash())) {
            throw new AppException("Your current password is incorrect.");
        }
        String err = Validators.checkPassword(newPassword);
        if (err != null) throw new AppException(err);
        if (newPassword.equals(current)) throw new AppException("Choose a password you haven't used before.");
        users.updatePassword(u.id(), PasswordHasher.hash(newPassword), false);
        Session.start(users.findById(u.id()));
    }

    // ---- user administration (coordinator only) --------------------------------------------

    public List<User> listUsers() {
        Permissions.require(Role.COORDINATOR);
        return users.listAll();
    }

    /** Creates a student or teacher with a temporary password that must be changed at first sign-in. */
    public String createUser(String name, String email, Role role, String department) {
        Permissions.require(Role.COORDINATOR);
        if (name == null || name.isBlank() || name.length() > 80) throw new AppException("Enter the person's full name.");
        String err = Validators.checkEmail(email);
        if (err != null) throw new AppException(err);
        if (role != Role.STUDENT && role != Role.TEACHER) throw new AppException("Choose Student or Teacher.");
        if (users.findByEmail(email) != null) throw new AppException("An account with that email already exists.");
        String temp = PasswordHasher.temporaryPassword();
        users.create(name, email, PasswordHasher.hash(temp), role, department, true);
        return temp;
    }

    public String resetPassword(int userId) {
        Permissions.require(Role.COORDINATOR);
        String temp = PasswordHasher.temporaryPassword();
        users.updatePassword(userId, PasswordHasher.hash(temp), true);
        return temp;
    }

    public void setActive(int userId, boolean active) {
        User me = Permissions.require(Role.COORDINATOR);
        if (me.id() == userId) throw new AppException("You can't disable your own account.");
        users.setActive(userId, active);
    }
}
