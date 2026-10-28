package com.sakec.smartspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.ClassSlot;
import com.sakec.smartspace.model.TimetableEntry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceTest {
    @Test
    void hourlyUsageSeparatesTimetableAndConfirmedBookingMinutes() {
        LocalDateTime start = LocalDate.of(2030, 4, 8).atTime(10, 30);
        TimetableEntry entry = new TimetableEntry(1, 1, "Room", "Physics", "Class A", 1, "Faculty",
                start, start.plusMinutes(90), "NONE", null, false);
        ClassSlot slot = new ClassSlot(entry, start, start.plusMinutes(90));
        Booking booking = new Booking(2, 1, "Room", 2, "Requester", "Study",
                start.plusMinutes(30), start.plusHours(2), BookingStatus.APPROVED,
                3, null, start.minusDays(1));

        List<ReportService.PeriodUtil> usage = ReportService.hourlyUsage(List.of(slot), List.of(booking));

        assertEquals(30, usage.get(10).scheduledMinutes());
        assertEquals(60, usage.get(11).scheduledMinutes());
        assertEquals(60, usage.get(11).confirmedBookingMinutes());
        assertEquals(30, usage.get(12).confirmedBookingMinutes());
    }

    @Test
    void dailyUsageSplitsIntervalsAtMidnight() {
        LocalDateTime start = LocalDate.of(2030, 4, 13).atTime(23, 30);
        TimetableEntry entry = new TimetableEntry(1, 1, "Room", "Physics", "Class A", 1, "Faculty",
                start, start.plusHours(1), "NONE", null, false);

        List<ReportService.PeriodUtil> usage = ReportService.dailyUsage(
                List.of(new ClassSlot(entry, start, start.plusHours(1))), List.of());

        assertEquals(30, usage.get(5).scheduledMinutes());
        assertEquals(30, usage.get(6).scheduledMinutes());
    }

    @Test
    void roomTimeUtilizationDoesNotDoubleCountOverlappingScheduleRecords() {
        LocalDateTime start = LocalDate.of(2030, 4, 8).atTime(10, 0);

        long minutes = ReportService.unionMinutes(List.of(
                new ReportService.TimeRange(start, start.plusHours(1)),
                new ReportService.TimeRange(start.plusMinutes(30), start.plusMinutes(90))),
                LocalTime.of(8, 0), LocalTime.of(18, 0));

        assertEquals(90, minutes);
    }

    @Test
    void roomTimeUtilizationExcludesSundaysFromConfiguredOperatingDays() {
        LocalDateTime start = LocalDate.of(2030, 4, 14).atTime(10, 0);

        long minutes = ReportService.unionMinutes(List.of(
                new ReportService.TimeRange(start, start.plusHours(1))),
                LocalTime.of(8, 0), LocalTime.of(18, 0));

        assertEquals(0, minutes);
    }
}
