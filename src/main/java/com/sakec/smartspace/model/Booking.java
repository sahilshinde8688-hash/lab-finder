package com.sakec.smartspace.model;

import java.time.LocalDateTime;

public record Booking(int id, int roomId, String roomName, int userId, String requesterName, String purpose,
                      LocalDateTime start, LocalDateTime end, BookingStatus status, Integer reviewedBy,
                      String reviewNote, LocalDateTime createdAt) { }
