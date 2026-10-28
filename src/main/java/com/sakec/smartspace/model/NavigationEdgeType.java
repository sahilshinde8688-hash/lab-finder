package com.sakec.smartspace.model;

public enum NavigationEdgeType {
    WALK("Walk"), STAIRCASE("Staircase"), LIFT("Lift");

    private final String label;

    NavigationEdgeType(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }
}
