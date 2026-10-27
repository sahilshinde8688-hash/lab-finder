package com.sakec.smartspace.model;

public enum Role {
    STUDENT("Student"), TEACHER("Teacher"), COORDINATOR("Timetable Coordinator");

    private final String label;

    Role(String label) { this.label = label; }

    public String label() { return label; }
}
