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
    record TimeRange(LocalDateTime start, LocalDateTime end) { }

    public record RoomUtil(Room room, long bookedMinutes, double percent, int confirmedBookingCount) { }
    public record PeriodUtil(String period, long scheduledMinutes, long confirmedBookingMinutes) { }

    public record Report(LocalDate from, LocalDate to, List<RoomUtil> utilization, double overallPercent,
                         Map<RoomType, Long> minutesByType, Map<BookingStatus, Integer> bookingStats,
                         List<MaintenanceRecord> maintenance, Map<Integer, int[]> floorNow,
                         List<PeriodUtil> byHour, List<PeriodUtil> byDay,
                         Map<Integer, Double> utilizationByFloor) { }

    private final RoomDao rooms = new RoomDao();
    private final BookingDao bookings = new BookingDao();
    private final MaintenanceDao maintenance = new MaintenanceDao();
    private final AvailabilityService availability = new AvailabilityService();
    private final TimetableService timetable = new TimetableService();

    public List<RoomUtil> roomUtilization(LocalDate from, LocalDate to) {
        Permissions.require(Role.COORDINATOR);
        if (from == null || to == null || to.isBefore(from)) throw new AppException("Choose a valid date range.");
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > 366) throw new AppException("Choose a range of at most one year.");

        LocalTime opStart = LocalTime.of(Config.getInt("ops.startHour", 8), 0);
        LocalTime opEnd = LocalTime.of(Config.getInt("ops.endHour", 18), 0);
        List<Room> active = rooms.listActive();
        List<TimetableEntry> entries = timetable.all();
        List<Booking> booked = bookings.blocking(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<ClassSlot> scheduled = TimetableService.expand(entries, from, to);

        long dayMinutes = Duration.between(opStart, opEnd).toMinutes();
        long opDays = from.datesUntil(to.plusDays(1)).filter(d -> d.getDayOfWeek() != DayOfWeek.SUNDAY).count();
        long capacity = Math.max(1, dayMinutes * opDays);

        Map<Integer, List<TimeRange>> intervalsByRoom = new TreeMap<>();
        Map<Integer, Integer> confirmedBookingCounts = new TreeMap<>();
        for (ClassSlot s : scheduled) {
            intervalsByRoom.computeIfAbsent(s.entry().roomId(), ignored -> new ArrayList<>())
                    .add(new TimeRange(s.start(), s.end()));
        }
        for (Booking b : booked) {
            intervalsByRoom.computeIfAbsent(b.roomId(), ignored -> new ArrayList<>())
                    .add(new TimeRange(b.start(), b.end()));
            confirmedBookingCounts.merge(b.roomId(), 1, Integer::sum);
        }

        List<RoomUtil> util = new ArrayList<>();
        for (Room r : active) {
            long roomMinutes = unionMinutes(intervalsByRoom.getOrDefault(r.id(), List.of()), opStart, opEnd);
            util.add(new RoomUtil(r, roomMinutes, Math.min(100.0, roomMinutes * 100.0 / capacity),
                    confirmedBookingCounts.getOrDefault(r.id(), 0)));
        }
        util.sort(Comparator.comparingLong(RoomUtil::bookedMinutes).reversed());
        return util;
    }

    public Report build(LocalDate from, LocalDate to) {
        return build(from, to, null);
    }

    public Report build(LocalDate from, LocalDate to, Map<Integer, int[]> floorNowOverride) {
        Permissions.require(Role.COORDINATOR);
        if (from == null || to == null || to.isBefore(from)) throw new AppException("Choose a valid date range.");
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > 366) throw new AppException("Choose a range of at most one year.");

        LocalTime opStart = LocalTime.of(Config.getInt("ops.startHour", 8), 0);
        LocalTime opEnd = LocalTime.of(Config.getInt("ops.endHour", 18), 0);
        List<Room> active = rooms.listActive();
        List<TimetableEntry> entries = timetable.all();
        List<Booking> booked = bookings.blocking(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<ClassSlot> scheduled = TimetableService.expand(entries, from, to);

        long dayMinutes = Duration.between(opStart, opEnd).toMinutes();
        long opDays = from.datesUntil(to.plusDays(1)).filter(d -> d.getDayOfWeek() != DayOfWeek.SUNDAY).count();
        long capacity = Math.max(1, dayMinutes * opDays);

        Map<Integer, List<TimeRange>> intervalsByRoom = new TreeMap<>();
        Map<Integer, Integer> confirmedBookingCounts = new TreeMap<>();
        for (ClassSlot s : scheduled) {
            intervalsByRoom.computeIfAbsent(s.entry().roomId(), ignored -> new ArrayList<>())
                    .add(new TimeRange(s.start(), s.end()));
        }
        for (Booking b : booked) {
            intervalsByRoom.computeIfAbsent(b.roomId(), ignored -> new ArrayList<>())
                    .add(new TimeRange(b.start(), b.end()));
            confirmedBookingCounts.merge(b.roomId(), 1, Integer::sum);
        }

        Map<Integer, Long> minutes = new TreeMap<>();
        List<RoomUtil> util = new ArrayList<>();
        Map<RoomType, Long> byType = new EnumMap<>(RoomType.class);
        long total = 0;
        for (Room r : active) {
            long roomMinutes = unionMinutes(intervalsByRoom.getOrDefault(r.id(), List.of()), opStart, opEnd);
            minutes.put(r.id(), roomMinutes);
            long m = roomMinutes;
            util.add(new RoomUtil(r, m, Math.min(100.0, m * 100.0 / capacity),
                    confirmedBookingCounts.getOrDefault(r.id(), 0)));
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
        Map<Integer, int[]> floorNow = floorNowOverride != null ? floorNowOverride : floorSummaryNow();
        return new Report(from, to, util, overall, byType, stats, maint, floorNow,
                hourlyUsage(scheduled, booked), dailyUsage(scheduled, booked),
                floorUtilization(active, minutes, capacity));
    }

    static List<PeriodUtil> hourlyUsage(List<ClassSlot> scheduled, List<Booking> booked) {
        Map<Integer, long[]> totals = new TreeMap<>();
        for (int hour = 0; hour < 24; hour++) totals.put(hour, new long[2]);
        for (ClassSlot slot : scheduled) accumulateByHour(totals, slot.start(), slot.end(), 0);
        for (Booking booking : booked) accumulateByHour(totals, booking.start(), booking.end(), 1);
        return totals.entrySet().stream()
                .map(entry -> new PeriodUtil(String.format("%02d:00", entry.getKey()),
                        entry.getValue()[0], entry.getValue()[1])).toList();
    }

    static List<PeriodUtil> dailyUsage(List<ClassSlot> scheduled, List<Booking> booked) {
        Map<DayOfWeek, long[]> totals = new java.util.EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) totals.put(day, new long[2]);
        for (ClassSlot slot : scheduled) accumulateByDay(totals, slot.start(), slot.end(), 0);
        for (Booking booking : booked) accumulateByDay(totals, booking.start(), booking.end(), 1);
        return java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> new PeriodUtil(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ROOT),
                        totals.get(day)[0], totals.get(day)[1])).toList();
    }

    private static void accumulateByHour(Map<Integer, long[]> totals, LocalDateTime start,
                                         LocalDateTime end, int series) {
        while (start.isBefore(end)) {
            LocalDateTime nextHour = start.toLocalDate().atStartOfDay().plusHours(start.getHour() + 1L);
            LocalDateTime segmentEnd = end.isBefore(nextHour) ? end : nextHour;
            totals.get(start.getHour())[series] += Duration.between(start, segmentEnd).toMinutes();
            start = segmentEnd;
        }
    }

    private static void accumulateByDay(Map<DayOfWeek, long[]> totals, LocalDateTime start,
                                        LocalDateTime end, int series) {
        while (start.isBefore(end)) {
            LocalDateTime nextDay = start.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime segmentEnd = end.isBefore(nextDay) ? end : nextDay;
            totals.get(start.getDayOfWeek())[series] += Duration.between(start, segmentEnd).toMinutes();
            start = segmentEnd;
        }
    }

    static long unionMinutes(List<TimeRange> intervals, LocalTime opStart, LocalTime opEnd) {
        List<TimeRange> clipped = new ArrayList<>();
        for (TimeRange interval : intervals) {
            LocalDateTime cursor = interval.start();
            while (cursor.isBefore(interval.end())) {
                LocalDateTime midnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
                LocalDateTime segmentEnd = interval.end().isBefore(midnight) ? interval.end() : midnight;
                if (cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    LocalDateTime open = cursor.toLocalDate().atTime(opStart);
                    LocalDateTime close = cursor.toLocalDate().atTime(opEnd);
                    LocalDateTime start = cursor.isAfter(open) ? cursor : open;
                    LocalDateTime end = segmentEnd.isBefore(close) ? segmentEnd : close;
                    if (end.isAfter(start)) clipped.add(new TimeRange(start, end));
                }
                cursor = segmentEnd;
            }
        }
        clipped.sort(Comparator.comparing(TimeRange::start));
        if (clipped.isEmpty()) return 0;
        long total = 0;
        LocalDateTime start = clipped.get(0).start();
        LocalDateTime end = clipped.get(0).end();
        for (int i = 1; i < clipped.size(); i++) {
            TimeRange next = clipped.get(i);
            if (!next.start().isAfter(end)) {
                if (next.end().isAfter(end)) end = next.end();
            } else {
                total += Duration.between(start, end).toMinutes();
                start = next.start();
                end = next.end();
            }
        }
        return total + Duration.between(start, end).toMinutes();
    }

    private static Map<Integer, Double> floorUtilization(List<Room> active, Map<Integer, Long> minutes,
                                                         long roomCapacity) {
        Map<Integer, long[]> totals = new TreeMap<>();
        Map<Integer, Integer> roomCounts = new TreeMap<>();
        for (Room room : active) {
            totals.computeIfAbsent(room.floor(), ignored -> new long[1])[0] += minutes.getOrDefault(room.id(), 0L);
            roomCounts.merge(room.floor(), 1, Integer::sum);
        }
        Map<Integer, Double> result = new TreeMap<>();
        totals.forEach((floor, values) -> {
            long capacity = Math.max(1, roomCapacity * roomCounts.get(floor));
            result.put(floor, Math.min(100.0, values[0] * 100.0 / capacity));
        });
        return result;
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

    // ---- CSV exports -----------------------------------------------------------------------

    public void exportUtilization(Path file, Report r) {
        Permissions.require(Role.COORDINATOR);
        List<List<String>> rows = new ArrayList<>();
        for (RoomUtil u : r.utilization()) {
            rows.add(List.of(u.room().name(), String.valueOf(u.room().floor()), u.room().type().label(),
                    String.format("%.1f", u.bookedMinutes() / 60.0), String.format("%.1f", u.percent())));
        }
        CsvExporter.write(file, List.of("Room", "Floor", "Type", "Scheduled + confirmed room hours (" + r.from() + " to " + r.to() + ")",
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
