package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TimetableDao {
    private static final String SELECT = "SELECT t.timetable_id, t.room_id, r.room_name, t.subject, t.class_name, "
            + "t.faculty_id, u.full_name, t.start_datetime, t.end_datetime, t.recurrence_rule, t.recurrence_until, "
            + "t.is_sample FROM timetable t JOIN rooms r ON r.room_id = t.room_id "
            + "JOIN users u ON u.user_id = t.faculty_id ";

    private static final Db.RowMapper<TimetableEntry> MAPPER = rs -> new TimetableEntry(rs.getInt(1), rs.getInt(2),
            rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getString(7),
            Dt.fromDb(rs.getString(8)), Dt.fromDb(rs.getString(9)), rs.getString(10),
            Dt.dateFromDb(rs.getString(11)), rs.getInt(12) == 1);

    public List<TimetableEntry> listAll() { return Db.query(SELECT + "ORDER BY t.start_datetime", MAPPER); }

    public List<TimetableEntry> listForFaculty(int facultyId) {
        return Db.query(SELECT + "WHERE t.faculty_id = ? ORDER BY t.start_datetime", MAPPER, facultyId);
    }

    public TimetableEntry findById(int id) { return Db.queryOne(SELECT + "WHERE t.timetable_id = ?", MAPPER, id); }

    public long insert(int roomId, String subject, String className, int facultyId, LocalDateTime start,
                       LocalDateTime end, String recurrence, LocalDate until) {
        return Db.insert("INSERT INTO timetable(room_id, subject, class_name, faculty_id, start_datetime, "
                + "end_datetime, recurrence_rule, recurrence_until) VALUES (?,?,?,?,?,?,?,?)",
                roomId, subject, className, facultyId, start, end, recurrence, until);
    }

    public void update(int id, int roomId, String subject, String className, int facultyId, LocalDateTime start,
                       LocalDateTime end, String recurrence, LocalDate until) {
        Db.update("UPDATE timetable SET room_id=?, subject=?, class_name=?, faculty_id=?, start_datetime=?, "
                + "end_datetime=?, recurrence_rule=?, recurrence_until=? WHERE timetable_id=?",
                roomId, subject, className, facultyId, start, end, recurrence, until, id);
    }

    public void delete(int id) { Db.update("DELETE FROM timetable WHERE timetable_id = ?", id); }
}
