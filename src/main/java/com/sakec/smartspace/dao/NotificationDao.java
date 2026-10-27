package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.Notification;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import java.util.List;

public class NotificationDao {
    private static final Db.RowMapper<Notification> MAPPER = rs -> new Notification(rs.getInt(1), rs.getInt(2),
            rs.getString(3), rs.getString(4), rs.getInt(5) == 1, Dt.fromDb(rs.getString(6)));

    public void insert(int userId, String title, String message) {
        Db.update("INSERT INTO notifications(user_id, title, message) VALUES (?,?,?)", userId, title, message);
    }

    public void insertForRole(Role role, String title, String message) {
        Db.update("INSERT INTO notifications(user_id, title, message) SELECT user_id, ?, ? FROM users "
                + "WHERE role = ? AND account_status = 'ACTIVE'", title, message, role);
    }

    public List<Notification> forUser(int userId, int limit) {
        return Db.query("SELECT notification_id, user_id, title, message, read_status, created_at FROM notifications "
                + "WHERE user_id = ? ORDER BY created_at DESC, notification_id DESC LIMIT ?", MAPPER, userId, limit);
    }

    public int unreadCount(int userId) {
        return Db.query("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_status = 0",
                rs -> rs.getInt(1), userId).get(0);
    }

    public void markRead(int id, int userId) {
        Db.update("UPDATE notifications SET read_status = 1 WHERE notification_id = ? AND user_id = ?", id, userId);
    }

    public void markAllRead(int userId) {
        Db.update("UPDATE notifications SET read_status = 1 WHERE user_id = ?", userId);
    }
}
