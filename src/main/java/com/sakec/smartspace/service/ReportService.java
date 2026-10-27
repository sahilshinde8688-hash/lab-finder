package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.BookingDao;
import com.sakec.smartspace.dao.MaintenanceDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.ClassSlot;
import com.sakec.smartspace.model.MaintenanceRecord;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.model.SearchCriteria;
import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Config;
import com.sakec.smartspace.util.CsvExporter;
import com.sakec.smartspace.util.Dt;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Analytics. Every number is calculated from the saved timetable, bookings and maintenance records. */
public class ReportService {
    public record RoomUtil(Room room, long bookedMinutes, double percent) { }

    public record Report(LocalDate from, LocalDate to, List<RoomUtil> utilization, double overallPercent,
                         Map<RoomType, Long> minutesByType, Map<BookingStatus, Integer> bookingStats,
                         List<MaintenanceRecord> maintenance, Map<Integer, int[]> floorNow) { }

    private final RoomDao rooms = new RoomDao();
    private final BookingDao bookings = new BookingDao();
    private final MaintenanceDao maintenance = new MaintenanceDao();
    private final AvailabilityService availability = new AvailabilityService();
    private final TimetableService timetable = new TimetableService();

    public Report build(LocalDate from, LocalDate to) {
        Permissions.require(Role.COORDINATOR);
        if (from == null || to == null || to.isBefore(from)) throw new AppException("Choose a valid date range.");
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > 366) throw new AppException("Choose a range of at most one year.");

        LocalTime opStart = LocalTime.of(Config.getInt("ops.startHour", 8), 0);
        LocalTime opEnd = LocalTime.of(Config.getInt("ops.endHour", 18), 0);
        List<Room> active = rooms.listActive();
        List<TimetableEntry> entries = timetable.all();
        List<Booking> booked = bookings.blocking(from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        long dayMinutes = Duration.between(opStart, opEnd).toMinutes();
        long opDays = from.datesUntil(to.plusDays(1)).filter(d -> d.getDayOfWeek() != DayOfWeek.SUNDAY).count();
        long capacity = Math.max(1, dayMinutes * opDays);

        Map<Integer, Long> minutes = new TreeMap<>();
        for (ClassSlot s : TimetableService.expand(entries, from, to)) {
            minutes.merge(s.entry().roomId(), clip(s.start(), s.end(), opStart, opEnd), Long::sum);
        }
        for (Booking b : booked) {
            minutes.merge(b.roomId(), clip(b.start(), b.end(), opStart, opEnd), Long::sum);
        }

        List<RoomUtil> util = new ArrayList<>();
        Map<RoomType, Long> byType = new EnumMap<>(RoomType.class);
        long total = 0;
        for (Room r : active) {
            long m = minutes.getOrDefault(r.id(), 0L);
            util.add(new RoomUtil(r, m, Math.min(100.0, m * 100.0 / capacity)));
            byType.merge(r.type(), m, Long::sum);
            total += m;
        }
        util.sort(Comparator.comparingLong(RoomUtil::bookedMinutes).reversed());
        double overall = active.isEmpty() ? 0 : Math.min(100.0, total * 100.0 / (capacity * active.size()));

        Map<BookingStatus, Integer> stats = new EnumMap<>(BookingStatus.class);
        for (BookingStatus st : BookingStatus.values()) stats.put(st, 0);
        for (Booking b : bookings.listAll()) {
            LocalDate d = b.start().toLocalDate();
            if (!d.isBefore(from) && !d.isAfter(to)) stats.merge(b.status(), 1, Integer::sum);
        }

        List<MaintenanceRecord> maint = maintenance.listOverlapping(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return new Report(from, to, util, overall, byType, stats, maint, floorSummaryNow());
    }

    /** Per floor: {available now, total active rooms}. */
    public Map<Integer, int[]> floorSummaryNow() {
        Map<Integer, int[]> map = new TreeMap<>();
        for (int f = 1; f <= 7; f++) map.put(f, new int[]{0, 0});
        for (RoomAvailability a : availability.search(SearchCriteria.nowOnly())) {
            int[] counts = map.get(a.room().floor());
            counts[1]++;
            if (a.status() == RoomStatus.AVAILABLE) counts[0]++;
        }
        return map;
    }

    private static long clip(LocalDateTime s, LocalDateTime e, LocalTime opStart, LocalTime opEnd) {
        LocalDateTime lo = s.toLocalDate().atTime(opStart), hi = s.toLocalDate().atTime(opEnd);
        LocalDateTime a = s.isAfter(lo) ? s : lo, b = e.isBefore(hi) ? e : hi;
        return b.isAfter(a) ? Duration.between(a, b).toMinutes() : 0;
    }

    // ---- CSV exports -----------------------------------------------------------------------

    public void exportUtilization(Path file, Report r) {
        Permissions.require(Role.COORDINATOR);
        List<List<String>> rows = new ArrayList<>();
        for (RoomUtil u : r.utilization()) {
            rows.add(List.of(u.room().name(), String.valueOf(u.room().floor()), u.room().type().label(),
                    String.format("%.1f", u.bookedMinutes() / 60.0), String.format("%.1f", u.percent())));
        }
        CsvExporter.write(file, List.of("Room", "Floor", "Type", "Booked hours (" + r.from() + " to " + r.to() + ")",
                "Utilization %"), rows);
    }

    public void exportTimetable(Path file, LocalDate from, LocalDate to) {
        Permissions.require(Role.COORDINATOR);
        List<List<String>> rows = new ArrayList<>();
        for (ClassSlot s : TimetableService.expand(timetable.all(), from, to)) {
            TimetableEntry e = s.entry();
            rows.add(List.of(s.start().format(Dt.DATE), s.start().format(Dt.TIME), s.end().format(Dt.TIME),
                    e.roomName(), e.subject(), e.className(), e.facultyName(), e.sample() ? "yes" : "no"));
        }
        CsvExporter.write(file, List.of("Date", "Start", "End", "Room", "Subject", "Class", "Faculty", "Sample data"), rows);
    }

    public void exportBookings(Path file, LocalDate from, LocalDate to) {
        Permissions.require(Role.COORDINATOR);
        List<List<String>> rows = new ArrayList<>();
        for (Booking b : bookings.listAll()) {
            LocalDate d = b.start().toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;
            rows.add(List.of(d.format(Dt.DATE), Dt.range(b.start(), b.end()), b.roomName(), b.requesterName(),
                    b.purpose(), b.status().label()));
        }
        CsvExporter.write(file, List.of("Date", "Time", "Room", "Requested by", "Purpose", "Status"), rows);
    }
}
