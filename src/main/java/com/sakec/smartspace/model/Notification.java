package com.sakec.smartspace.model;

import java.time.LocalDateTime;

public record Notification(int id, int userId, String title, String message, boolean read, LocalDateTime createdAt) { }
