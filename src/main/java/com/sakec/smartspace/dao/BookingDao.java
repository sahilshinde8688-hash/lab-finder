package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

public class BookingDao {
    private static final String SELECT = "SELECT b.booking_id, b.room_id, r.room_name, b.user_id, u.full_name, "
            + "b.purpose, b.start_datetime, b.end_datetime, b.booking_status, b.reviewed_by, b.review_note, "
            + "b.created_at FROM bookings b JOIN rooms r ON r.room_id = b.room_id "
            + "JOIN users u ON u.user_id = b.user_id ";

    private static final Db.RowMapper<Booking> MAPPER = rs -> new Booking(rs.getInt(1), rs.getInt(2), rs.getString(3),
            rs.getInt(4), rs.getString(5), rs.getString(6), Dt.fromDb(rs.getString(7)), Dt.fromDb(rs.getString(8)),
            BookingStatus.valueOf(rs.getString(9)), rs.getObject(10) == null ? null : rs.getInt(10),
            rs.getString(11), Dt.fromDb(rs.getString(12)));

    public Booking findById(Connection c, int id) {
        List<Booking> l = Db.query(c, SELECT + "WHERE b.booking_id = ?", MAPPER, id);
        return l.isEmpty() ? null : l.get(0);
    }

    public Booking findById(int id) { return Db.queryOne(SELECT + "WHERE b.booking_id = ?", MAPPER, id); }

    public List<Booking> listByUser(int userId) {
        return Db.query(SELECT + "WHERE b.user_id = ? ORDER BY b.start_datetime DESC", MAPPER, userId);
    }

    public List<Booking> listByStatus(BookingStatus status) {
        return Db.query(SELECT + "WHERE b.booking_status = ? ORDER BY b.start_datetime", MAPPER, status);
    }

    public List<Booking> listAll() { return Db.query(SELECT + "ORDER BY b.start_datetime DESC", MAPPER); }

    /** Approved or completed bookings that overlap [from, to). Pass a connection to stay inside a transaction. */
    public List<Booking> blocking(Connection c, LocalDateTime from, LocalDateTime to) {
        return Db.query(c, SELECT + "WHERE b.booking_status IN ('APPROVED','COMPLETED') "
                + "AND b.start_datetime < ? AND b.end_datetime > ? ORDER BY b.start_datetime", MAPPER, to, from);
    }

    public List<Booking> blocking(LocalDateTime from, LocalDateTime to) {
        return Db.query(SELECT + "WHERE b.booking_status IN ('APPROVED','COMPLETED') "
                + "AND b.start_datetime < ? AND b.end_datetime > ? ORDER BY b.start_datetime", MAPPER, to, from);
    }

    public long insert(Connection c, int roomId, int userId, String purpose, LocalDateTime start, LocalDateTime end) {
        return Db.insert(c, "INSERT INTO bookings(room_id, user_id, purpose, start_datetime, end_datetime) "
                + "VALUES (?,?,?,?,?)", roomId, userId, purpose, start, end);
    }

    public void review(Connection c, int id, BookingStatus status, int reviewerId, String note) {
        Db.update(c, "UPDATE bookings SET booking_status = ?, reviewed_by = ?, review_note = ? WHERE booking_id = ?",
                status, reviewerId, note, id);
    }

    public void setStatus(Connection c, int id, BookingStatus status) {
        Db.update(c, "UPDATE bookings SET booking_status = ? WHERE booking_id = ?", status, id);
    }

    public int completePast(LocalDateTime now) {
        return Db.update("UPDATE bookings SET booking_status = 'COMPLETED' WHERE booking_status = 'APPROVED' "
                + "AND end_datetime < ?", now);
    }
}
