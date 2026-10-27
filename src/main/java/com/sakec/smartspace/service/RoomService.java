package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.util.AppException;
import java.util.List;

public class RoomService {
    private final RoomDao dao = new RoomDao();

    public List<Room> activeRooms() { return dao.listActive(); }

    public List<Room> allRooms() { return dao.listAll(); }

    public Room find(int id) { return dao.findById(id); }

    public void recordView(int roomId) { dao.recordView(Session.user().id(), roomId); }

    public void save(Integer id, String name, int floor, RoomType type, int capacity, String facilities) {
        Permissions.require(Role.COORDINATOR);
        if (name == null || name.isBlank() || name.length() > 30) throw new AppException("Enter a room name or number (up to 30 characters).");
        if (floor < 1 || floor > 7) throw new AppException("Floor must be between 1 and 7.");
        if (type == null) throw new AppException("Choose a room type.");
        if (capacity < 1 || capacity > 1000) throw new AppException("Capacity must be between 1 and 1000.");
        if (facilities != null && facilities.length() > 200) throw new AppException("Facilities text is too long.");
        if (dao.nameTaken(name, id == null ? -1 : id)) throw new AppException("A room with that name already exists.");
        if (id == null) dao.insert(name, floor, type, capacity, facilities);
        else dao.update(id, name, floor, type, capacity, facilities);
    }

    public void setActive(int id, boolean active) {
        Permissions.require(Role.COORDINATOR);
        dao.setActive(id, active);
    }
}
