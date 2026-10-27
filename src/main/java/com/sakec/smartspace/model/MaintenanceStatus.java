package com.sakec.smartspace.model;

public enum MaintenanceStatus {
    REPORTED("Reported", "badge-pending"), IN_PROGRESS("In Progress", "badge-maintenance"),
    RESOLVED("Resolved", "badge-available");

    private final String label;
    private final String css;

    MaintenanceStatus(String label, String css) { this.label = label; this.css = css; }

    public String label() { return label; }

    public String css() { return css; }
}
