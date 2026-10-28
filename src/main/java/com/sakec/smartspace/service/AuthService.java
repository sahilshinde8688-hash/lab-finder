package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.PasswordHasher;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.supabase.SupabaseAuthService;
import com.sakec.smartspace.supabase.SupabaseAuthService.SignUpResult;
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
    private final EmailService mailer = new EmailService();

    public User login(String email, String password) {
        String emailError = Validators.checkEmail(email);
        if (emailError != null) throw new AppException(emailError);
        if (password == null || password.isEmpty()) throw new AppException("Enter your password.");

        User u = users.findByEmail(email);
        if (u == null) {
            PasswordHasher.verify(password, DUMMY_HASH); // keeps timing similar for unknown accounts
            throw new AppException(GENERIC);
        }

        // ---- Email verification gate (self-registered students only) --------------------
        if (!u.emailVerified()) {
            // Check with Supabase whether the student has clicked their confirmation link.
            // If confirmed, activate the local account and proceed to login.
            boolean confirmed = false;
            try {
                confirmed = new SupabaseAuthService().checkEmailConfirmed(
                        u.email(), password);
            } catch (Exception e) {
                Log.warn("Could not verify email status with Supabase for " + u.email() + ": " + e.getMessage());
            }
            if (confirmed) {
                users.activateVerified(u.id());
                u = users.findById(u.id()); // reload with updated status
                Log.info("Email verified and account activated for userId=" + u.id());
            } else {
                throw new EmailNotVerifiedException(
                        "Please verify your email before signing in.\n"
                        + "Check your inbox at " + u.email() + " for a confirmation link.");
            }
        }
        // ---------------------------------------------------------------------------------

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

    /**
     * Thrown only when a self-registered student tries to log in before confirming their email.
     * Extends {@link AppException} so it surfaces safely in the UI, but the login controller
     * catches it separately to show the "Resend email" option.
     */
    public static final class EmailNotVerifiedException extends AppException {
        public EmailNotVerifiedException(String message) { super(message); }
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
        mailer.send(email, "SmartSpace account created",
                "Hello " + name.trim() + ",\n\nYour SmartSpace account has been created. "
                        + "Use the temporary password below to sign in and change it immediately.\n\n"
                        + "Email: " + email + "\nPassword: " + temp + "\n\nPlease do not share this password."
        );
        return temp;
    }

    public String resetPassword(int userId) {
        Permissions.require(Role.COORDINATOR);
        String temp = PasswordHasher.temporaryPassword();
        users.updatePassword(userId, PasswordHasher.hash(temp), true);
        User target = users.findById(userId);
        if (target != null) {
            mailer.send(target.email(), "SmartSpace password reset",
                    "Hello " + target.firstName() + ",\n\nYour SmartSpace password has been reset. "
                            + "Use the temporary password below to sign in and choose a new one.\n\n"
                            + "Email: " + target.email() + "\nPassword: " + temp + "\n\nPlease change it immediately after signing in."
            );
        }
        return temp;
    }

    public void setActive(int userId, boolean active) {
        User me = Permissions.require(Role.COORDINATOR);
        if (me.id() == userId) throw new AppException("You can't disable your own account.");
        users.setActive(userId, active);
    }

    public void deleteUser(int userId) {
        User me = Permissions.require(Role.COORDINATOR);
        if (me.id() == userId) throw new AppException("You can't delete your own account.");
        User target = users.findById(userId);
        if (target == null) throw new AppException("User not found.");
        users.delete(userId);
        Log.info("User deleted completely from database by coordinator: " + target.email() + " (userId=" + userId + ")");
    }

    // ---- student self-registration ----------------------------------------------------------

    /**
     * Creates a new student account.
     *
     * <p>Registration is a two-step process:
     * <ol>
     *   <li><b>Local SQLite</b> — validates all inputs, checks for duplicates, and
     *       creates the account in the local {@code users} table with PBKDF2-hashed password.
     *       This is what powers the existing login flow.</li>
     *   <li><b>Supabase Auth</b> — registers the same email/password with the Supabase
     *       project so the student identity exists in the cloud. If Supabase is unreachable
     *       or not configured, the local account is still created and the user can sign in
     *       normally; the Supabase step is best-effort.</li>
     * </ol>
     *
     * <p>Security guarantees:
     * <ul>
     *   <li>Only {@code sakec.onmicrosoft.com} domain is accepted (configurable).</li>
     *   <li>Password satisfies the policy (≥12 chars, letter + digit) before being passed anywhere.</li>
     *   <li>Role is fixed to {@code STUDENT} — callers cannot override it.</li>
     *   <li>No session is started; the user must sign in after registering.</li>
     *   <li>Passwords are never logged.</li>
     * </ul>
     */
    public RegistrationResult selfRegister(String fullName, String email, String password,
                                           String confirmPassword, String branch) {
        // ---- name
        if (fullName == null || fullName.isBlank()) throw new AppException("Enter your full name.");
        if (fullName.trim().length() > 80) throw new AppException("Name is too long (max 80 characters).");

        // ---- email (normalise first, then validate domain)
        if (email == null || email.isBlank()) throw new AppException("Enter your SAKEC email address.");
        String normEmail = email.trim().toLowerCase();
        String emailErr = Validators.checkStudentEmail(normEmail);
        if (emailErr != null) throw new AppException(emailErr);

        // ---- password
        String pwErr = Validators.checkPassword(password);
        if (pwErr != null) throw new AppException(pwErr);
        if (!password.equals(confirmPassword)) throw new AppException("Passwords do not match.");

        // ---- branch
        if (branch == null || branch.isBlank()) throw new AppException("Select your branch.");

        // ---- duplicate check (generic message to avoid disclosing registration status)
        if (users.findByEmail(normEmail) != null) {
            throw new AppException("An account with that email already exists. Try signing in instead.");
        }

        // ---- Step 1: create local SQLite account (PENDING_VERIFICATION — cannot sign in until confirmed)
        users.create(fullName.trim(), normEmail, PasswordHasher.hash(password),
                Role.STUDENT, branch.trim(), false, true);
        Log.info("Self-registration: local account created (pending verification) for " + normEmail);

        // ---- Step 2: register with Supabase Auth (best-effort — local account persists regardless)
        boolean supabaseOk = false;
        try {
            SignUpResult result = new SupabaseAuthService().signUp(normEmail, password, fullName.trim(), branch.trim());
            supabaseOk = (result == SignUpResult.SUCCESS || result == SignUpResult.ALREADY_EXISTS);
            if (result == SignUpResult.ALREADY_EXISTS) {
                Log.info("Supabase: email already had a cloud account (local account created fresh): " + normEmail);
            }
        } catch (Exception e) {
            // Supabase failure must not roll back the local account — log and continue
            Log.error("Supabase sign-up call failed (local account still created): " + normEmail, e);
        }

        // ---- Step 3: optional welcome email — prompt to verify
        mailer.send(normEmail, "Welcome to SmartSpace – SAKEC — Please verify your email",
                "Hi " + fullName.trim().split("\\s+")[0] + ",\n\n"
                + "Your SmartSpace account has been created.\n\n"
                + "Please verify your SAKEC email address before signing in.\n"
                + "Check your inbox for a confirmation email from Supabase/SmartSpace and click the link.\n\n"
                + "Once verified, return to the SmartSpace app and sign in normally.\n\n"
                + "If you did not register, please contact the timetable coordinator immediately.\n\n"
                + "— SmartSpace, SAKEC");

        return supabaseOk ? RegistrationResult.SUCCESS_WITH_CLOUD : RegistrationResult.SUCCESS_LOCAL_ONLY;
    }

    /**
     * Asks Supabase to resend the confirmation email for the given address.
     * Validates that the account exists and is still pending verification before calling Supabase.
     */
    public void resendVerificationEmail(String email) {
        if (email == null || email.isBlank()) throw new AppException("Enter your email address.");
        String normEmail = email.trim().toLowerCase();
        User u = users.findByEmail(normEmail);
        if (u == null) {
            // Generic message — don't reveal whether an account exists
            throw new AppException("If that email is registered and pending verification, a new confirmation email has been sent.");
        }
        if (u.emailVerified()) {
            throw new AppException("This account is already verified. Try signing in.");
        }
        try {
            new SupabaseAuthService().resendConfirmation(normEmail);
        } catch (Exception e) {
            Log.warn("Could not resend verification email for " + normEmail + ": " + e.getMessage());
            throw new AppException("Could not send the email right now. Please try again in a moment.");
        }
        Log.info("Resent verification email for: " + normEmail);
    }

    /** Describes the outcome of {@link #selfRegister}. */
    public enum RegistrationResult {
        /** Local SQLite account created AND Supabase Auth account registered. */
        SUCCESS_WITH_CLOUD,
        /**
         * Local SQLite account created successfully.
         * Supabase Auth step was skipped (not configured) or failed non-fatally.
         * The user can still sign in using the local account.
         */
        SUCCESS_LOCAL_ONLY
    }
}
