package com.sakec.smartspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakec.smartspace.model.NavigationEdge;
import com.sakec.smartspace.model.NavigationEdgeType;
import com.sakec.smartspace.model.NavigationGraph;
import com.sakec.smartspace.model.NavigationNode;
import com.sakec.smartspace.model.NavigationNodeType;
import com.sakec.smartspace.model.NavigationPath;
import com.sakec.smartspace.model.NavigationRoomLink;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.service.NavigationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavigationServiceTest {
    private static final List<Room> ROOMS = List.of(
            new Room(1, "Room 401", 4, RoomType.CLASSROOM, 30, "", true, false),
            new Room(2, "Room 501", 5, RoomType.LABORATORY, 30, "", true, false));

    @Test
    void dijkstraUsesVerifiedShortestCorridorPathAcrossFloors() {
        NavigationGraph graph = validGraph(true);

        var result = NavigationService.calculateRoute(graph, ROOMS, 1, 2, false);

        assertTrue(result.found(), result.diagnostic());
        NavigationPath path = result.path();
        assertEquals(List.of("a", "x", "lift4", "lift5", "b"),
                path.nodes().stream().map(NavigationNode::id).toList());
        assertEquals(5.0, path.distanceMeters());
    }

    @Test
    void validationReportsDisconnectedCorridorNodes() {
        NavigationGraph graph = new NavigationGraph(1, false,
                List.of(node("a", 4, NavigationNodeType.CORRIDOR),
                        node("b", 4, NavigationNodeType.JUNCTION),
                        node("c", 5, NavigationNodeType.CORRIDOR)),
                List.of(), List.of());

        var validation = NavigationService.validate(graph, List.of());

        assertTrue(validation.issues().stream().anyMatch(issue -> issue.code().equals("disconnected-node")));
    }

    @Test
    void validationRejectsMismatchedFloorTransitionNodeTypes() {
        NavigationGraph graph = new NavigationGraph(1, false,
                List.of(node("lift4", 4, NavigationNodeType.LIFT),
                        node("stairs5", 5, NavigationNodeType.STAIRCASE)),
                List.of(edge("transition", "lift4", "stairs5", NavigationEdgeType.LIFT, 1.0, false)),
                List.of());

        var validation = NavigationService.validate(graph, List.of());

        assertTrue(validation.issues().stream().anyMatch(issue -> issue.code().equals("invalid-floor-transition")));
    }

    @Test
    void accessibleRouteDoesNotUseUnverifiedAccessibleConnection() {
        NavigationGraph graph = validGraph(false);

        var result = NavigationService.calculateRoute(graph, ROOMS, 1, 2, true);

        assertFalse(result.found());
        assertTrue(result.diagnostic().contains("unreachable using verified accessible paths"));
    }

    @Test
    void routeExplainsWhenStartingFloorHasNoNavigationNodes() {
        var result = NavigationService.calculateRoute(NavigationGraph.empty(), ROOMS, 1, 2, false);

        assertFalse(result.found());
        assertTrue(result.diagnostic().contains("No navigation nodes configured for Floor 4"));
    }

    @Test
    void validationDetectsDuplicateConnectionsRegardlessOfDirection() {
        NavigationGraph graph = validGraph(true);
        List<NavigationEdge> edges = new java.util.ArrayList<>(graph.edges());
        edges.add(edge("duplicate-ax", "x", "a", NavigationEdgeType.WALK, 1.0, true));
        NavigationGraph duplicateGraph = new NavigationGraph(graph.revisionId(), false,
                graph.nodes(), edges, graph.roomLinks());

        var validation = NavigationService.validate(duplicateGraph, ROOMS);

        assertTrue(validation.issues().stream().anyMatch(issue -> issue.code().equals("duplicate-connection")));
    }

    private static NavigationGraph validGraph(boolean transitionAccessible) {
        List<NavigationNode> nodes = List.of(
                node("a", 4, NavigationNodeType.CORRIDOR),
                node("x", 4, NavigationNodeType.JUNCTION),
                node("y", 4, NavigationNodeType.JUNCTION),
                node("lift4", 4, NavigationNodeType.LIFT),
                node("lift5", 5, NavigationNodeType.LIFT),
                node("b", 5, NavigationNodeType.CORRIDOR));
        List<NavigationEdge> edges = List.of(
                edge("ax", "a", "x", NavigationEdgeType.WALK, 1.0, true),
                edge("xl4", "x", "lift4", NavigationEdgeType.WALK, 1.0, true),
                edge("ay", "a", "y", NavigationEdgeType.WALK, 3.0, true),
                edge("yl4", "y", "lift4", NavigationEdgeType.WALK, 3.0, true),
                edge("l4l5", "lift4", "lift5", NavigationEdgeType.LIFT, 2.0, transitionAccessible),
                edge("l5b", "lift5", "b", NavigationEdgeType.WALK, 1.0, true));
        return new NavigationGraph(1, true, nodes, edges,
                List.of(new NavigationRoomLink(1, "a"), new NavigationRoomLink(2, "b")));
    }

    private static NavigationNode node(String id, int floor, NavigationNodeType type) {
        return new NavigationNode(id, floor, type, id, .25, .25, true);
    }

    private static NavigationEdge edge(String id, String from, String to, NavigationEdgeType type,
                                        double distance, boolean accessible) {
        return new NavigationEdge(id, from, to, type, distance, true, accessible, accessible, true);
    }
}
