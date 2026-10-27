package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RoomDao {
    private static final String SELECT = "SELECT room_id, room_name, floor_number, room_type, capacity, facilities, "
            + "active_status, is_sample FROM rooms ";

    private static final Db.RowMapper<Room> MAPPER = rs -> new Room(rs.getInt("room_id"), rs.getString("room_name"),
            rs.getInt("floor_number"), RoomType.valueOf(rs.getString("room_type")), rs.getInt("capacity"),
            rs.getString("facilities"), rs.getInt("active_status") == 1, rs.getInt("is_sample") == 1);

    public Room findById(int id) { return Db.queryOne(SELECT + "WHERE room_id = ?", MAPPER, id); }

    public List<Room> search(Integer floor, RoomType type, Integer minCapacity, String equipment, boolean includeInactive) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> p = new ArrayList<>();
        if (!includeInactive) sql.append("AND active_status = 1 ");
        if (floor != null) { sql.append("AND floor_number = ? "); p.add(floor); }
        if (type != null) { sql.append("AND room_type = ? "); p.add(type); }
        if (minCapacity != null) { sql.append("AND capacity >= ? "); p.add(minCapacity); }
        if (equipment != null && !equipment.isBlank()) {
            sql.append("AND facilities LIKE ? "); p.add("%" + equipment.trim() + "%");
        }
        sql.append("ORDER BY floor_number, room_name");
        return Db.query(sql.toString(), MAPPER, p.toArray());
    }

    public List<Room> listActive() { return search(null, null, null, null, false); }

    public List<Room> listAll() { return search(null, null, null, null, true); }

    public long insert(String name, int floor, RoomType type, int capacity, String facilities) {
        return Db.insert("INSERT INTO rooms(room_name, floor_number, room_type, capacity, facilities) "
                + "VALUES (?,?,?,?,?)", name.trim(), floor, type, capacity, facilities == null ? "" : facilities.trim());
    }

    public void update(int id, String name, int floor, RoomType type, int capacity, String facilities) {
        Db.update("UPDATE rooms SET room_name=?, floor_number=?, room_type=?, capacity=?, facilities=? WHERE room_id=?",
                name.trim(), floor, type, capacity, facilities == null ? "" : facilities.trim(), id);
    }

    public void setActive(int id, boolean active) {
        Db.update("UPDATE rooms SET active_status = ? WHERE room_id = ?", active, id);
    }

    public boolean nameTaken(String name, int exceptId) {
        return !Db.query("SELECT 1 FROM rooms WHERE room_name = ? AND room_id <> ?", rs -> 1, name.trim(), exceptId).isEmpty();
    }

    public void recordView(int userId, int roomId) {
        Db.update("INSERT OR REPLACE INTO recent_views(user_id, room_id, viewed_at) VALUES (?,?,?)",
                userId, roomId, Dt.toDb(LocalDateTime.now()));
    }

    public List<Room> recentlyViewed(int userId, int limit) {
        return Db.query("SELECT r.room_id, r.room_name, r.floor_number, r.room_type, r.capacity, r.facilities, "
                + "r.active_status, r.is_sample FROM recent_views v JOIN rooms r ON r.room_id = v.room_id "
                + "WHERE v.user_id = ? AND r.active_status = 1 ORDER BY v.viewed_at DESC LIMIT ?", MAPPER, userId, limit);
    }
}
