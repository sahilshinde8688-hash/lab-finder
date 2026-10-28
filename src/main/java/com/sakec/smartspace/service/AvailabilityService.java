package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.BookingDao;
import com.sakec.smartspace.dao.MaintenanceDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.dao.TimetableDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.MaintenanceRecord;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.SearchCriteria;
import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Dt;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out whether rooms are free for a time window, using the official timetable, approved bookings and
 * maintenance restrictions. Priority: Unavailable, Under Maintenance, Occupied, Reserved, Available.
 */
public class AvailabilityService {
    private final RoomDao rooms = new RoomDao();
    private final TimetableDao timetable = new TimetableDao();
    private final BookingDao bookings = new BookingDao();
    private final MaintenanceDao maintenance = new MaintenanceDao();

    /** Everything that can block a room in a window. */
    public record Snapshot(List<TimetableEntry> timetable, List<Booking> bookings, List<MaintenanceRecord> maintenance) { }

    public List<RoomAvailability> search(SearchCriteria c) {
        checkWindow(c.from(), c.to());
        List<Room> found = rooms.search(c.floor(), c.type(), c.minCapacity(), c.equipment(), c.includeInactive());
        Snapshot snap = snapshot(null, c.from(), c.to());
        List<RoomAvailability> out = new ArrayList<>();
        for (Room r : found) {
            RoomAvailability a = compute(r, c.from(), c.to(), snap);
            if (!c.availableOnly() || a.status() == RoomStatus.AVAILABLE) out.add(a);
        }
        return out;
    }

    public RoomAvailability check(Connection tx, Room room, LocalDateTime from, LocalDateTime to) {
        checkWindow(from, to);
        return compute(room, from, to, snapshot(tx, from, to));
    }

    public RoomAvailability check(int roomId, LocalDateTime from, LocalDateTime to) {
        Room room = rooms.findById(roomId);
        if (room == null) throw new AppException("That room no longer exists.");
        return check(null, room, from, to);
    }

    public Snapshot snapshot(Connection tx, LocalDateTime from, LocalDateTime to) {
        LocalDateTime wide = to.isAfter(from.toLocalDate().plusDays(1).atStartOfDay())
                ? to : from.toLocalDate().plusDays(1).atStartOfDay();
        List<Booking> b = tx == null ? bookings.blocking(from, wide) : bookings.blocking(tx, from, wide);
        return new Snapshot(timetable.listAll(), b, maintenance.blocking(from, to));
    }

    public static RoomAvailability compute(Room room, LocalDateTime from, LocalDateTime to, Snapshot s) {
        if (!room.active()) return new RoomAvailability(room, RoomStatus.UNAVAILABLE, "This room is deactivated");

        for (MaintenanceRecord m : s.maintenance()) {
            if (m.roomId() == room.id()) {
                return new RoomAvailability(room, RoomStatus.UNDER_MAINTENANCE, shorten(m.description()));
            }
        }
        List<TimetableEntry> scheduledConflicts = new ArrayList<>();
        LocalDateTime firstScheduledEnd = null;
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            for (TimetableEntry e : s.timetable()) {
                if (e.roomId() != room.id() || !e.occursOn(d)) continue;
                LocalDateTime st = e.startOn(d), en = e.endOn(d);
                if (Dt.overlaps(from, to, st, en)) {
                    scheduledConflicts.add(e);
                    if (firstScheduledEnd == null) firstScheduledEnd = en;
                }
            }
        }
        List<Booking> bookingConflicts = s.bookings().stream()
                .filter(b -> b.roomId() == room.id() && blocksAvailability(b.status())
                        && Dt.overlaps(from, to, b.start(), b.end()))
                .toList();
        int conflictCount = scheduledConflicts.size() + bookingConflicts.size();
        if (conflictCount > 1) {
            RoomStatus status = scheduledConflicts.isEmpty() ? RoomStatus.RESERVED : RoomStatus.OCCUPIED;
            return new RoomAvailability(room, status, "Conflicting schedule records overlap this slot; treat the room as "
                    + status.label().toLowerCase() + " until the records are resolved.");
        }
        if (!scheduledConflicts.isEmpty()) {
            TimetableEntry e = scheduledConflicts.get(0);
            return new RoomAvailability(room, RoomStatus.OCCUPIED,
                    e.subject() + " · " + e.className() + " until " + firstScheduledEnd.format(Dt.TIME));
        }
        if (!bookingConflicts.isEmpty()) {
            Booking b = bookingConflicts.get(0);
            return new RoomAvailability(room, RoomStatus.RESERVED,
                    "Reserved until " + b.end().format(Dt.TIME) + " · " + shorten(b.purpose()));
        }
        LocalDateTime nextStart = null;
        // Available: find the next thing that will start later the same day
        LocalDate day = to.toLocalDate();
        for (TimetableEntry e : s.timetable()) {
            if (e.roomId() == room.id() && e.occursOn(day) && !e.startOn(day).isBefore(to)) {
                if (nextStart == null || e.startOn(day).isBefore(nextStart)) nextStart = e.startOn(day);
            }
        }
        for (Booking b : s.bookings()) {
            if (b.roomId() == room.id() && blocksAvailability(b.status())
                    && !b.start().isBefore(to) && b.start().toLocalDate().equals(day)) {
                if (nextStart == null || b.start().isBefore(nextStart)) nextStart = b.start();
            }
        }
        String detail = nextStart == null ? "Free for the rest of the day" : "Free until " + nextStart.format(Dt.TIME);
        return new RoomAvailability(room, RoomStatus.AVAILABLE, detail);
    }

    private static boolean blocksAvailability(BookingStatus status) {
        return status == BookingStatus.APPROVED || status == BookingStatus.COMPLETED;
    }

    private static void checkWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) throw new AppException("Choose a start and end time.");
        if (!to.isAfter(from)) throw new AppException("The end time must be after the start time.");
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 48 ? s.substring(0, 45) + "…" : s;
    }
}
