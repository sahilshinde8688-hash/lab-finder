package com.sakec.smartspace.model;

import java.util.List;

public record NavigationGraph(long revisionId, boolean published, List<NavigationNode> nodes,
                              List<NavigationEdge> edges, List<NavigationRoomLink> roomLinks) {
    public NavigationGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        roomLinks = List.copyOf(roomLinks);
    }

    public static NavigationGraph empty() {
        return new NavigationGraph(0, false, List.of(), List.of(), List.of());
    }
}
