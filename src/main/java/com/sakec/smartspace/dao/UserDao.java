package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Log;
import java.time.LocalDateTime;
import java.util.List;

public class UserDao {
    private static final String SELECT = "SELECT user_id, full_name, institutional_email, password_hash, role, "
            + "department, account_status, must_change_password, failed_attempts, locked_until FROM users ";

    private static final Db.RowMapper<User> MAPPER = rs -> {
        String status = rs.getString("account_status");
        boolean active   = "ACTIVE".equals(status);
        boolean verified = !"PENDING_VERIFICATION".equals(status); // ACTIVE and DISABLED are both "verified"
        return new User(
                rs.getInt("user_id"),
                rs.getString("full_name"),
                rs.getString("institutional_email"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                rs.getString("department"),
                active,
                rs.getInt("must_change_password") == 1,
                rs.getInt("failed_attempts"),
                Dt.fromDb(rs.getString("locked_until")),
                verified);
    };

    public User findByEmail(String email) {
        return Db.queryOne(SELECT + "WHERE institutional_email = ?", MAPPER, email.trim());
    }

    public User findById(int id) { return Db.queryOne(SELECT + "WHERE user_id = ?", MAPPER, id); }

    public List<User> listAll() { return Db.query(SELECT + "ORDER BY role, full_name", MAPPER); }

    public List<User> listByRole(Role role) {
        return Db.query(SELECT + "WHERE role = ? AND account_status = 'ACTIVE' ORDER BY full_name", MAPPER, role);
    }

    /**
     * Creates an account. Self-registered students are created with
     * {@code account_status = 'PENDING_VERIFICATION'} (pass {@code pendingVerification = true}).
     * Coordinator-created accounts and seed accounts pass {@code pendingVerification = false}
     * and get {@code account_status = 'ACTIVE'}.
     */
    public long create(String name, String email, String hash, Role role, String department,
                       boolean mustChange, boolean pendingVerification) {
        String status = pendingVerification ? "PENDING_VERIFICATION" : "ACTIVE";
        return Db.insert(
                "INSERT INTO users(full_name, institutional_email, password_hash, role, department, "
                + "must_change_password, account_status) VALUES (?,?,?,?,?,?,?)",
                name.trim(), email.trim(), hash, role,
                department == null ? null : department.trim(),
                mustChange, status);
    }

    /**
     * Legacy overload — keeps all existing callers (Bootstrap, AuthService.createUser) compiling
     * without changes. Always creates an ACTIVE account.
     */
    public long create(String name, String email, String hash, Role role, String department, boolean mustChange) {
        return create(name, email, hash, role, department, mustChange, false);
    }

    /** Marks a PENDING_VERIFICATION account as ACTIVE after successful email confirmation. */
    public void activateVerified(int id) {
        Db.update("UPDATE users SET account_status = 'ACTIVE' WHERE user_id = ? "
                + "AND account_status = 'PENDING_VERIFICATION'", id);
    }

    public void updatePassword(int id, String hash, boolean mustChange) {
        Db.update("UPDATE users SET password_hash = ?, must_change_password = ?, failed_attempts = 0, "
                + "locked_until = NULL WHERE user_id = ?", hash, mustChange, id);
    }

    public void recordFailure(int id, int attempts, LocalDateTime lockedUntil) {
        Db.update("UPDATE users SET failed_attempts = ?, locked_until = ? WHERE user_id = ?",
                attempts, lockedUntil == null ? null : Dt.toDb(lockedUntil), id);
    }

    public void clearFailures(int id) {
        Db.update("UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?", id);
    }

    public void setActive(int id, boolean active) {
        Db.update("UPDATE users SET account_status = ? WHERE user_id = ?", active ? "ACTIVE" : "DISABLED", id);
    }

    public void delete(int id) {
        User u = findById(id);
        if (u == null) return;
        Db.inTransaction(c -> {
            Db.update(c, "DELETE FROM notifications WHERE user_id = ?", id);
            Db.update(c, "DELETE FROM recent_views WHERE user_id = ?", id);
            Db.update(c, "UPDATE bookings SET reviewed_by = NULL WHERE reviewed_by = ?", id);
            Db.update(c, "DELETE FROM bookings WHERE user_id = ?", id);
            Db.update(c, "DELETE FROM maintenance WHERE reported_by = ?", id);
            Db.update(c, "DELETE FROM timetable WHERE faculty_id = ?", id);
            Db.update(c, "DELETE FROM users WHERE user_id = ?", id);
            return null;
        });
        if (Db.isPostgres()) {
            try {
                Db.update("DELETE FROM auth.users WHERE lower(email) = lower(?)", u.email());
            } catch (Exception e) {
                Log.warn("Could not delete from auth.users: " + e.getMessage());
            }
        }
    }

    public int count() { return Db.query("SELECT COUNT(*) FROM users", rs -> rs.getInt(1)).get(0); }
}
