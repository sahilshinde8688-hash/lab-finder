package com.sakec.smartspace.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Date/time helpers. Database values are ISO strings (yyyy-MM-ddTHH:mm:ss) so they sort correctly. */
public final class Dt {
    public static final DateTimeFormatter DB = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Dt() { }

    public static String toDb(LocalDateTime t) { return t == null ? null : t.format(DB); }

    public static LocalDateTime fromDb(String s) { return s == null ? null : LocalDateTime.parse(s, DB); }

    public static String toDb(LocalDate d) { return d == null ? null : d.atStartOfDay().format(DB); }

    public static LocalDate dateFromDb(String s) { return s == null ? null : LocalDateTime.parse(s, DB).toLocalDate(); }

    /** Two half-open intervals [aStart,aEnd) and [bStart,bEnd) overlap. Touching ends do not overlap. */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public static String range(LocalDateTime s, LocalDateTime e) {
        return s.format(TIME) + " – " + e.format(TIME);
    }

    public static String dateTime(LocalDateTime t) { return t.format(DATE) + ", " + t.format(TIME); }

    public static LocalTime roundUp(LocalTime t, int minutes) {
        int total = t.getHour() * 60 + t.getMinute();
        int rounded = ((total + minutes - 1) / minutes) * minutes;
        return rounded >= 24 * 60 ? LocalTime.of(23, 45) : LocalTime.of(rounded / 60, rounded % 60);
    }
}
