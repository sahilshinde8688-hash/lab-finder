package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.MaintenanceRecord;
import com.sakec.smartspace.model.MaintenanceStatus;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDateTime;
import java.util.List;

public class MaintenanceDao {
    private static final String SELECT = "SELECT m.maintenance_id, m.room_id, r.room_name, m.description, "
            + "m.start_datetime, m.end_datetime, m.maintenance_status, m.blocks_room, m.reported_by, u.full_name "
            + "FROM maintenance m JOIN rooms r ON r.room_id = m.room_id JOIN users u ON u.user_id = m.reported_by ";

    private static final Db.RowMapper<MaintenanceRecord> MAPPER = rs -> new MaintenanceRecord(rs.getInt(1),
            rs.getInt(2), rs.getString(3), rs.getString(4), Dt.fromDb(rs.getString(5)), Dt.fromDb(rs.getString(6)),
            MaintenanceStatus.valueOf(rs.getString(7)), rs.getInt(8) == 1, rs.getInt(9), rs.getString(10));

    public long insert(int roomId, String description, int reportedBy) {
        return Db.insert("INSERT INTO maintenance(room_id, description, start_datetime, reported_by) VALUES (?,?,?,?)",
                roomId, description, LocalDateTime.now().withNano(0), reportedBy);
    }

    public MaintenanceRecord findById(int id) { return Db.queryOne(SELECT + "WHERE m.maintenance_id = ?", MAPPER, id); }

    public List<MaintenanceRecord> listAll() { return Db.query(SELECT + "ORDER BY m.start_datetime DESC", MAPPER); }

    public List<MaintenanceRecord> listByReporter(int userId) {
        return Db.query(SELECT + "WHERE m.reported_by = ? ORDER BY m.start_datetime DESC", MAPPER, userId);
    }

    public List<MaintenanceRecord> listOverlapping(LocalDateTime from, LocalDateTime to) {
        return Db.query(SELECT + "WHERE m.start_datetime < ? AND (m.end_datetime IS NULL OR m.end_datetime > ?) "
                + "ORDER BY m.start_datetime DESC", MAPPER, to, from);
    }

    /** Active records that make a room unusable (blocks_room, not resolved) overlapping the window. */
    public List<MaintenanceRecord> blocking(LocalDateTime from, LocalDateTime to) {
        return Db.query(SELECT + "WHERE m.blocks_room = 1 AND m.maintenance_status <> 'RESOLVED' "
                + "AND m.start_datetime < ? AND (m.end_datetime IS NULL OR m.end_datetime > ?)", MAPPER, to, from);
    }

    public int countOpen() {
        return Db.query("SELECT COUNT(*) FROM maintenance WHERE maintenance_status <> 'RESOLVED'", rs -> rs.getInt(1)).get(0);
    }

    public void updateStatus(int id, MaintenanceStatus status, boolean blocks) {
        LocalDateTime end = status == MaintenanceStatus.RESOLVED ? LocalDateTime.now().withNano(0) : null;
        Db.update("UPDATE maintenance SET maintenance_status = ?, blocks_room = ?, end_datetime = ? WHERE maintenance_id = ?",
                status, blocks, end, id);
    }
}
