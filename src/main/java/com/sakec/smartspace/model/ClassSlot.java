package com.sakec.smartspace.model;

import java.time.LocalDateTime;

/** One concrete occurrence of a timetable entry on a given day. */
public record ClassSlot(TimetableEntry entry, LocalDateTime start, LocalDateTime end) { }
