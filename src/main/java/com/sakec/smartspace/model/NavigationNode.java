package com.sakec.smartspace.model;

public record NavigationNode(String id, int floor, NavigationNodeType type, String label,
                             double x, double y, boolean verified) {
    @Override public String toString() { return label + " · Floor " + floor + " (" + type.label() + ")"; }
}
