package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDateTime;
import java.util.List;

public class UserDao {
    private static final String SELECT = "SELECT user_id, full_name, institutional_email, password_hash, role, "
            + "department, account_status, must_change_password, failed_attempts, locked_until FROM users ";

    private static final Db.RowMapper<User> MAPPER = rs -> new User(
            rs.getInt("user_id"), rs.getString("full_name"), rs.getString("institutional_email"),
            rs.getString("password_hash"), Role.valueOf(rs.getString("role")), rs.getString("department"),
            "ACTIVE".equals(rs.getString("account_status")), rs.getInt("must_change_password") == 1,
            rs.getInt("failed_attempts"), Dt.fromDb(rs.getString("locked_until")));

    public User findByEmail(String email) {
        return Db.queryOne(SELECT + "WHERE institutional_email = ?", MAPPER, email.trim());
    }

    public User findById(int id) { return Db.queryOne(SELECT + "WHERE user_id = ?", MAPPER, id); }

    public List<User> listAll() { return Db.query(SELECT + "ORDER BY role, full_name", MAPPER); }

    public List<User> listByRole(Role role) {
        return Db.query(SELECT + "WHERE role = ? AND account_status = 'ACTIVE' ORDER BY full_name", MAPPER, role);
    }

    public long create(String name, String email, String hash, Role role, String department, boolean mustChange) {
        return Db.insert("INSERT INTO users(full_name, institutional_email, password_hash, role, department, "
                + "must_change_password) VALUES (?,?,?,?,?,?)", name.trim(), email.trim(), hash, role,
                department == null ? null : department.trim(), mustChange);
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

    public int count() { return Db.query("SELECT COUNT(*) FROM users", rs -> rs.getInt(1)).get(0); }
}
