package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.NotificationDao;
import com.sakec.smartspace.model.Notification;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.util.Log;
import java.util.List;

public class NotificationService {
    private final NotificationDao dao = new NotificationDao();

    public List<Notification> mine(int limit) { return dao.forUser(Session.user().id(), limit); }

    public int unreadCount() { return dao.unreadCount(Session.user().id()); }

    public void markRead(int id) { dao.markRead(id, Session.user().id()); }

    public void markAllRead() { dao.markAllRead(Session.user().id()); }

    /** Notifications are best-effort: a failure to notify never blocks the main action. */
    public void notifyUser(int userId, String title, String message) {
        try {
            dao.insert(userId, title, message);
        } catch (RuntimeException e) {
            Log.error("Could not store notification", e);
        }
    }

    public void notifyCoordinators(String title, String message) {
        try {
            dao.insertForRole(Role.COORDINATOR, title, message);
        } catch (RuntimeException e) {
            Log.error("Could not store coordinator notification", e);
        }
    }
}
