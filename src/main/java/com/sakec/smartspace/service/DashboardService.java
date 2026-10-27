package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.BookingDao;
import com.sakec.smartspace.dao.MaintenanceDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.ClassSlot;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.SearchCriteria;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Session;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Gathers everything the three dashboards need in one background call. */
public class DashboardService {
    public record DashboardData(int totalRooms, int classrooms, int labs, int availableNow, int occupiedNow,
                                List<RoomAvailability> nowStatus, List<ClassSlot> todayAll, List<ClassSlot> todayMine,
                                List<Room> recentRooms, List<Room> assignedRooms, int pendingRequests, int myPending,
                                int openMaintenance, List<String> conflicts, List<Booking> recentBookings,
                                Map<Integer, int[]> floors) { }

    private final AvailabilityService availability = new AvailabilityService();
    private final TimetableService timetable = new TimetableService();
    private final BookingDao bookings = new BookingDao();
    private final RoomDao rooms = new RoomDao();
    private final MaintenanceDao maintenance = new MaintenanceDao();

    public DashboardData load() {
        User me = Session.user();
        LocalDate today = LocalDate.now();
        List<RoomAvailability> now = availability.search(SearchCriteria.nowOnly());
        int classrooms = 0, labs = 0, free = 0, busy = 0;
        Map<Integer, int[]> floors = new TreeMap<>();
        for (int f = 1; f <= 7; f++) floors.put(f, new int[]{0, 0});
        for (RoomAvailability a : now) {
            if (a.room().type().isLab()) labs++; else classrooms++;
            int[] fl = floors.get(a.room().floor());
            fl[1]++;
            if (a.status() == RoomStatus.AVAILABLE) { free++; fl[0]++; }
            if (a.status() == RoomStatus.OCCUPIED || a.status() == RoomStatus.RESERVED) busy++;
        }
        List<ClassSlot> todayAll = timetable.slotsOn(today);
        List<ClassSlot> mine = todayAll.stream().filter(s -> s.entry().facultyId() == me.id()).toList();

        List<Room> assigned = new ArrayList<>();
        if (me.role() == Role.TEACHER) {
            Map<Integer, Room> seen = new LinkedHashMap<>();
            for (var e : timetable.all()) {
                if (e.facultyId() == me.id() && !seen.containsKey(e.roomId())) {
                    Room r = rooms.findById(e.roomId());
                    if (r != null) seen.put(r.id(), r);
                }
            }
            assigned.addAll(seen.values());
        }
        int pending = me.role() == Role.COORDINATOR ? bookings.listByStatus(BookingStatus.PENDING).size() : 0;
        List<Booking> mineBookings = me.role() == Role.COORDINATOR ? List.of() : bookings.listByUser(me.id());
        int myPending = (int) mineBookings.stream().filter(b -> b.status() == BookingStatus.PENDING).count();
        List<String> conflicts = me.role() == Role.COORDINATOR ? timetable.detectConflicts(today, 7) : List.of();

        return new DashboardData(now.size(), classrooms, labs, free, busy, now, todayAll, mine,
                rooms.recentlyViewed(me.id(), 5), assigned, pending, myPending, maintenance.countOpen(), conflicts,
                mineBookings.stream().limit(5).toList(), floors);
    }
}
