package com.sakec.smartspace.model;

public record NavigationEdge(String id, String fromNodeId, String toNodeId, NavigationEdgeType type,
                             Double distanceMeters, boolean distanceVerified, boolean accessible,
                             boolean accessibilityVerified, boolean verified) {
    @Override public String toString() { return fromNodeId + " → " + toNodeId + " · " + type.label(); }
}
