package com.sakec.smartspace.model;

import java.time.LocalDateTime;

public record MaintenanceRecord(int id, int roomId, String roomName, String description, LocalDateTime start,
                                LocalDateTime end, MaintenanceStatus status, boolean blocksRoom,
                                int reportedBy, String reporterName) { }
