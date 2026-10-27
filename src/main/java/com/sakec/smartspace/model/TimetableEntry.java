package com.sakec.smartspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One timetable row. For recurrence "WEEKLY" the first occurrence is start/end and the entry repeats on the
 * same weekday until recurrenceUntil (inclusive). Entries always start and end on the same day.
 */
public record TimetableEntry(int id, int roomId, String roomName, String subject, String className,
                             int facultyId, String facultyName, LocalDateTime start, LocalDateTime end,
                             String recurrence, LocalDate until, boolean sample) {

    public boolean weekly() { return "WEEKLY".equals(recurrence); }

    public boolean occursOn(LocalDate d) {
        LocalDate first = start.toLocalDate();
        if (d.isBefore(first)) return false;
        if (!weekly()) return d.equals(first);
        if (d.getDayOfWeek() != first.getDayOfWeek()) return false;
        return until == null || !d.isAfter(until);
    }

    public LocalDateTime startOn(LocalDate d) { return d.atTime(start.toLocalTime()); }

    public LocalDateTime endOn(LocalDate d) { return d.atTime(end.toLocalTime()); }
}
