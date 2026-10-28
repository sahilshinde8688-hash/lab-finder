package com.sakec.smartspace.model;

import java.time.LocalDateTime;

public record User(int id, String fullName, String email, String passwordHash, Role role, String department,
                   boolean active, boolean mustChangePassword, int failedAttempts, LocalDateTime lockedUntil,
                   boolean emailVerified) {

    /**
     * Legacy constructor for accounts that pre-date email verification
     * (coordinators, teachers, seed data). These are always considered verified.
     */
    public User(int id, String fullName, String email, String passwordHash, Role role, String department,
                boolean active, boolean mustChangePassword, int failedAttempts, LocalDateTime lockedUntil) {
        this(id, fullName, email, passwordHash, role, department, active, mustChangePassword,
             failedAttempts, lockedUntil, true);
    }

    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    public String firstName() { return fullName.trim().split("\\s+")[0]; }

    /** Returns true when this account can be signed into (active AND email verified). */
    public boolean canSignIn() { return active && emailVerified; }
}
