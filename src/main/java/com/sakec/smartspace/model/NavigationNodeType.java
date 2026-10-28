package com.sakec.smartspace.model;

public enum NavigationNodeType {
    CORRIDOR("Corridor"), JUNCTION("Junction"), ENTRANCE("Entrance"), STAIRCASE("Staircase"), LIFT("Lift");

    private final String label;

    NavigationNodeType(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }
}
