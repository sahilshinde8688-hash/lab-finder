package com.sakec.smartspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.service.AvailabilityService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityServiceTest {
    private static final LocalDateTime FROM = LocalDateTime.of(2030, 4, 8, 10, 0);
    private static final LocalDateTime TO = FROM.plusHours(1);
    private static final Room ROOM = new Room(1, "Classroom 401", 4, RoomType.CLASSROOM,
            60, "", true, false);

    @Test
    void pendingBookingDoesNotBlockAvailability() {
        Booking pending = booking(BookingStatus.PENDING, FROM, TO);

        var result = AvailabilityService.compute(ROOM, FROM, TO,
                new AvailabilityService.Snapshot(List.of(), List.of(pending), List.of()));

        assertEquals(RoomStatus.AVAILABLE, result.status());
    }

    @Test
    void approvedBookingIsReserved() {
        Booking approved = booking(BookingStatus.APPROVED, FROM, TO);

        var result = AvailabilityService.compute(ROOM, FROM, TO,
                new AvailabilityService.Snapshot(List.of(), List.of(approved), List.of()));

        assertEquals(RoomStatus.RESERVED, result.status());
    }

    @Test
    void overlappingTimetableEntriesAreReportedAsConflictAndTreatedAsOccupied() {
        TimetableEntry first = timetable(1, "Physics");
        TimetableEntry second = timetable(2, "Chemistry");

        var result = AvailabilityService.compute(ROOM, FROM, TO,
                new AvailabilityService.Snapshot(List.of(first, second), List.of(), List.of()));

        assertEquals(RoomStatus.OCCUPIED, result.status());
        assertTrue(result.detail().contains("Conflicting schedule records"));
    }

    @Test
    void timetableAndApprovedBookingConflictIsStillBlocked() {
        TimetableEntry scheduled = timetable(1, "Physics");
        Booking approved = booking(BookingStatus.APPROVED, FROM, TO);

        var result = AvailabilityService.compute(ROOM, FROM, TO,
                new AvailabilityService.Snapshot(List.of(scheduled), List.of(approved), List.of()));

        assertEquals(RoomStatus.OCCUPIED, result.status());
        assertTrue(result.detail().contains("Conflicting schedule records"));
    }

    private static TimetableEntry timetable(int id, String subject) {
        return new TimetableEntry(id, ROOM.id(), ROOM.name(), subject, "Class A", 1, "Faculty",
                FROM, TO, "NONE", null, false);
    }

    private static Booking booking(BookingStatus status, LocalDateTime start, LocalDateTime end) {
        return new Booking(1, ROOM.id(), ROOM.name(), 2, "Requester", "Study", start, end,
                status, null, null, FROM.minusDays(1));
    }
}
