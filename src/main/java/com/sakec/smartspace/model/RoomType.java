package com.sakec.smartspace.model;

public enum RoomType {
    CLASSROOM("Classroom"), COMPUTER_LAB("Computer Lab"), LABORATORY("Laboratory"), SEMINAR_ROOM("Seminar Room");

    private final String label;

    RoomType(String label) { this.label = label; }

    public String label() { return label; }

    public boolean isLab() { return this == COMPUTER_LAB || this == LABORATORY; }

    @Override public String toString() { return label; }
}
