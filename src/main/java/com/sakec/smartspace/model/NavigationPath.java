package com.sakec.smartspace.model;

import java.util.List;

public record NavigationPath(List<NavigationNode> nodes, List<NavigationEdge> edges,
                             Double distanceMeters, boolean accessible) {
    public NavigationPath {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
