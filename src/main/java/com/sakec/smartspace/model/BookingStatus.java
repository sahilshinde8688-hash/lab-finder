package com.sakec.smartspace.model;

public enum BookingStatus {
    PENDING("Pending", "badge-pending"), APPROVED("Approved", "badge-available"),
    REJECTED("Rejected", "badge-occupied"), CANCELLED("Cancelled", "badge-unavailable"),
    COMPLETED("Completed", "badge-reserved");

    private final String label;
    private final String css;

    BookingStatus(String label, String css) { this.label = label; this.css = css; }

    public String label() { return label; }

    public String css() { return css; }
}
