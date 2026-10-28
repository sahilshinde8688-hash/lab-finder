package com.sakec.smartspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.model.TimetableEntry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityEstimationServiceTest {
    @Test
    void ranksCurrentlyClearRoomsByComparableHistoricalDemand() {
        LocalDateTime future = LocalDate.now().plusWeeks(1).atTime(10, 0);
        LocalDate previousWeek = LocalDate.now().minusWeeks(1);
        Room highDemand = room(1, "Classroom 401");
        Room lowDemand = room(2, "Classroom 402");
        TimetableEntry history = timetable(1, highDemand, previousWeek.atTime(10, 0),
                previousWeek.atTime(11, 0));
        AvailabilityService.Snapshot snapshot = new AvailabilityService.Snapshot(
                List.of(history), List.of(), List.of());

        List<AvailabilityEstimationService.Recommendation> ranked = AvailabilityEstimationService.rank(
                List.of(highDemand, lowDemand), future, future.plusHours(1), snapshot, List.of());

        assertEquals(lowDemand.id(), ranked.get(0).availability().room().id());
        assertEquals(1, ranked.get(1).historicallyUsedSlots());
    }

    @Test
    void confirmedConflictsAreNeverIncludedInPredictions() {
        LocalDateTime future = LocalDate.now().plusWeeks(1).atTime(10, 0);
        Room room = room(1, "Classroom 401");
        TimetableEntry conflict = timetable(1, room, future, future.plusHours(1));
        AvailabilityService.Snapshot snapshot = new AvailabilityService.Snapshot(
                List.of(conflict), List.of(), List.of());

        List<AvailabilityEstimationService.Recommendation> ranked = AvailabilityEstimationService.rank(
                List.of(room), future, future.plusHours(1), snapshot, List.of());

        assertEquals(List.of(), ranked);
    }

    @Test
    void pendingRequestsDoNotInflateHistoricalDemand() {
        LocalDateTime future = LocalDate.now().plusWeeks(1).atTime(10, 0);
        LocalDateTime past = LocalDate.now().minusWeeks(1).atTime(10, 0);
        Room room = room(1, "Classroom 401");
        Booking pending = new Booking(1, room.id(), room.name(), 2, "Requester", "Study",
                past, past.plusHours(1), BookingStatus.PENDING, null, null, past.minusDays(1));

        List<AvailabilityEstimationService.Recommendation> ranked = AvailabilityEstimationService.rank(
                List.of(room), future, future.plusHours(1),
                new AvailabilityService.Snapshot(List.of(), List.of(), List.of()), List.of(pending));

        assertEquals(0, ranked.get(0).historicallyUsedSlots());
    }

    @Test
    void approvedBookingsCountAsHistoricalDemand() {
        LocalDateTime future = LocalDate.now().plusWeeks(1).atTime(10, 0);
        LocalDateTime past = LocalDate.now().minusWeeks(1).atTime(10, 0);
        Room room = room(1, "Classroom 401");
        Booking approved = new Booking(1, room.id(), room.name(), 2, "Requester", "Study",
                past, past.plusHours(1), BookingStatus.APPROVED, 3, null, past.minusDays(1));

        List<AvailabilityEstimationService.Recommendation> ranked = AvailabilityEstimationService.rank(
                List.of(room), future, future.plusHours(1),
                new AvailabilityService.Snapshot(List.of(), List.of(), List.of()), List.of(approved));

        assertEquals(1, ranked.get(0).historicallyUsedSlots());
    }

    private static Room room(int id, String name) {
        return new Room(id, name, 4, RoomType.CLASSROOM, 60, "", true, false);
    }

    private static TimetableEntry timetable(int id, Room room, LocalDateTime start, LocalDateTime end) {
        return new TimetableEntry(id, room.id(), room.name(), "Physics", "Class A", 1, "Faculty",
                start, end, "NONE", null, false);
    }
}
