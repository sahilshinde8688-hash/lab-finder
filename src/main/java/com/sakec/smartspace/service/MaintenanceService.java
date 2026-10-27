package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.MaintenanceDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.MaintenanceRecord;
import com.sakec.smartspace.model.MaintenanceStatus;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.util.AppException;
import java.util.List;

public class MaintenanceService {
    private final MaintenanceDao dao = new MaintenanceDao();
    private final RoomDao rooms = new RoomDao();
    private final NotificationService notifications = new NotificationService();

    /** Any signed-in user can report an equipment or maintenance problem. */
    public void reportIssue(int roomId, String description) {
        User me = Session.user();
        Room room = rooms.findById(roomId);
        if (room == null) throw new AppException("Choose a room.");
        if (description == null || description.isBlank()) throw new AppException("Describe the problem.");
        if (description.trim().length() > 300) throw new AppException("Keep the description under 300 characters.");
        dao.insert(roomId, description.trim(), me.id());
        notifications.notifyCoordinators("Issue reported in " + room.name(), me.fullName() + ": " + description.trim());
    }

    /** Reports that a room shows as free but people are using it. Goes to the coordinators as a notification. */
    public void reportOccupied(int roomId, String details) {
        User me = Session.user();
        Room room = rooms.findById(roomId);
        if (room == null) throw new AppException("Choose a room.");
        String extra = details == null || details.isBlank() ? "" : " Details: " + details.trim();
        if (extra.length() > 300) throw new AppException("Keep the details under 300 characters.");
        notifications.notifyCoordinators("Room in use: " + room.name(),
                me.fullName() + " reports " + room.name() + " is occupied although it is shown as free." + extra);
    }

    public List<MaintenanceRecord> visible() {
        User me = Session.user();
        return me.role() == Role.COORDINATOR ? dao.listAll() : dao.listByReporter(me.id());
    }

    public void startWork(int id) { change(id, MaintenanceStatus.IN_PROGRESS, true); }

    public void resolve(int id) { change(id, MaintenanceStatus.RESOLVED, false); }

    private void change(int id, MaintenanceStatus status, boolean blocks) {
        Permissions.require(Role.COORDINATOR);
        MaintenanceRecord m = dao.findById(id);
        if (m == null) throw new AppException("That record no longer exists.");
        dao.updateStatus(id, status, blocks);
        notifications.notifyUser(m.reportedBy(), "Maintenance update",
                "Your report for " + m.roomName() + " is now: " + status.label() + ".");
    }
}
