package com.sakec.smartspace.model;

public enum RoomStatus {
    AVAILABLE("Available", "badge-available"),
    OCCUPIED("Occupied", "badge-occupied"),
    RESERVED("Reserved", "badge-reserved"),
    UNDER_MAINTENANCE("Under Maintenance", "badge-maintenance"),
    UNAVAILABLE("Unavailable", "badge-unavailable");

    private final String label;
    private final String css;

    RoomStatus(String label, String css) { this.label = label; this.css = css; }

    public String label() { return label; }

    public String css() { return css; }
}
