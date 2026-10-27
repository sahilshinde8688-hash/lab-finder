package com.sakec.smartspace.model;

import java.time.LocalDateTime;

public record User(int id, String fullName, String email, String passwordHash, Role role, String department,
                   boolean active, boolean mustChangePassword, int failedAttempts, LocalDateTime lockedUntil) {

    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    public String firstName() { return fullName.trim().split("\\s+")[0]; }
}
