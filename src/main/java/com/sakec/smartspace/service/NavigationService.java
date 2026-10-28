package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.NavigationDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.NavigationEdge;
import com.sakec.smartspace.model.NavigationEdgeType;
import com.sakec.smartspace.model.NavigationGraph;
import com.sakec.smartspace.model.NavigationIssue;
import com.sakec.smartspace.model.NavigationNode;
import com.sakec.smartspace.model.NavigationNodeType;
import com.sakec.smartspace.model.NavigationPath;
import com.sakec.smartspace.model.NavigationResult;
import com.sakec.smartspace.model.NavigationRoomLink;
import com.sakec.smartspace.model.NavigationValidation;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.util.AppException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class NavigationService {
    private final NavigationDao dao;
    private final RoomDao rooms;

    public NavigationService() { this(new NavigationDao(), new RoomDao()); }

    NavigationService(NavigationDao dao, RoomDao rooms) {
        this.dao = dao;
        this.rooms = rooms;
    }

    public NavigationGraph draft() {
        Permissions.require(Role.COORDINATOR);
        return dao.draft();
    }

    public NavigationGraph published() { return dao.published();     }

    public void saveNode(NavigationNode node) {
        NavigationGraph graph = requireDraft();
        if (node == null || node.id() == null || node.id().isBlank() || node.type() == null
                || node.label() == null || node.label().isBlank() || node.label().length() > 80
                || (node.floor() != 4 && node.floor() != 5)
                || !Double.isFinite(node.x()) || !Double.isFinite(node.y())
                || node.x() < 0 || node.x() > 1 || node.y() < 0 || node.y() > 1) {
            throw new AppException("Enter a label and valid map position on Floor 4 or 5.");
        }
        dao.saveNode(graph.revisionId(), node);
    }

    public void deleteNode(String nodeId) {
        long revision = requireDraft().revisionId();
        dao.deleteNode(revision, nodeId);
    }

    public void saveEdge(NavigationEdge edge) {
        NavigationGraph graph = requireDraft();
        if (edge == null || edge.id() == null || edge.id().isBlank() || edge.fromNodeId() == null
                || edge.toNodeId() == null || edge.fromNodeId().equals(edge.toNodeId()) || edge.type() == null) {
            throw new AppException("Choose two different navigation nodes and a connection type.");
        }
        if (edge.distanceMeters() != null
                && (!Double.isFinite(edge.distanceMeters()) || edge.distanceMeters() <= 0)) {
            throw new AppException("Distance must be a positive number of metres or left blank.");
        }
        if (edge.distanceMeters() != null && !edge.distanceVerified()) {
            throw new AppException("Mark the distance verified before saving it, or leave it blank.");
        }
        if (edge.accessible() && !edge.accessibilityVerified()) {
            throw new AppException("An accessible path must have its accessibility verified.");
        }
        NavigationNode from = graph.nodes().stream().filter(n -> n.id().equals(edge.fromNodeId())).findFirst().orElse(null);
        NavigationNode to = graph.nodes().stream().filter(n -> n.id().equals(edge.toNodeId())).findFirst().orElse(null);
        if (from == null || to == null) throw new AppException("Both connection endpoints must exist in the draft graph.");
        if (from.floor() == to.floor() && edge.type() != NavigationEdgeType.WALK) {
            throw new AppException("Connections on the same floor must be marked Walk.");
        }
        if (from.floor() != to.floor()) {
            if (edge.type() == NavigationEdgeType.WALK) {
                throw new AppException("A floor transition must be marked Staircase or Lift.");
            }
            NavigationNodeType required = edge.type() == NavigationEdgeType.LIFT
                    ? NavigationNodeType.LIFT : NavigationNodeType.STAIRCASE;
            if (from.type() != required || to.type() != required) {
                throw new AppException("Both ends of a floor transition must be verified " + edge.type().label().toLowerCase()
                        + " nodes.");
            }
        }
        dao.saveEdge(graph.revisionId(), edge);
    }

    public void deleteEdge(String edgeId) {
        long revision = requireDraft().revisionId();
        dao.deleteEdge(revision, edgeId);
    }

    public void saveRoomLink(NavigationRoomLink link) {
        NavigationGraph graph = requireDraft();
        if (link == null || link.nodeId() == null || link.nodeId().isBlank()) {
            throw new AppException("Choose a room and a corridor node.");
        }
        Room room = rooms.findById(link.roomId());
        if (room == null || !room.active() || (room.floor() != 4 && room.floor() != 5)) {
            throw new AppException("Select an active room on Floor 4 or 5.");
        }
        NavigationNode node = graph.nodes().stream().filter(n -> n.id().equals(link.nodeId())).findFirst().orElse(null);
        if (node == null || node.floor() != room.floor()) {
            throw new AppException("The selected corridor node must be on the room's floor.");
        }
        dao.saveRoomLink(graph.revisionId(), link);
    }

    public void deleteRoomLink(int roomId) {
        long revision = requireDraft().revisionId();
        dao.deleteRoomLink(revision, roomId);
    }

    public NavigationValidation validateDraft() {
        return validate(requireDraft(), rooms.listActive());
    }

    public NavigationValidation publish() {
        NavigationGraph graph = requireDraft();
        NavigationValidation validation = validate(graph, rooms.listActive());
        if (!validation.valid()) throw new AppException("Navigation graph failed validation. Resolve all "
                + validation.issues().size() + " issue(s) before publishing.");
        dao.publish(graph);
        return validation;
    }

    public NavigationResult calculateRoute(int fromRoomId, int toRoomId, boolean accessible) {
        return calculateRoute(dao.published(), rooms.listActive(), fromRoomId, toRoomId, accessible);
    }

    private NavigationGraph requireDraft() {
        Permissions.require(Role.COORDINATOR);
        return dao.draft();
    }

    public static NavigationValidation validate(NavigationGraph graph, List<Room> activeRooms) {
        List<NavigationIssue> issues = new ArrayList<>();
        Map<String, NavigationNode> nodes = new HashMap<>();
        for (NavigationNode node : graph.nodes()) {
            if (nodes.put(node.id(), node) != null) issue(issues, "duplicate-node", "Duplicate navigation node: " + node.id());
            if (node.floor() != 4 && node.floor() != 5)
                issue(issues, "invalid-floor", "Node " + node.label() + " uses unsupported floor " + node.floor() + ".");
            if (!Double.isFinite(node.x()) || !Double.isFinite(node.y())
                    || node.x() < 0 || node.x() > 1 || node.y() < 0 || node.y() > 1)
                issue(issues, "invalid-position", "Node " + node.label() + " is outside the floor-plan image.");
            if (!node.verified()) issue(issues, "unverified-node", "Node is not verified: " + node.label() + " (Floor " + node.floor() + ").");
        }
        for (int floor : List.of(4, 5)) {
            if (graph.nodes().stream().noneMatch(n -> n.floor() == floor))
                issue(issues, "missing-floor-nodes", "No navigation nodes configured for Floor " + floor + ".");
        }

        Set<String> pairs = new HashSet<>();
        boolean validFloorTransition = false;
        for (NavigationEdge edge : graph.edges()) {
            NavigationNode from = nodes.get(edge.fromNodeId());
            NavigationNode to = nodes.get(edge.toNodeId());
            if (from == null || to == null) {
                issue(issues, "missing-endpoint", "Connection " + edge.id() + " refers to a missing navigation node.");
                continue;
            }
            String pair = from.id().compareTo(to.id()) < 0 ? from.id() + "\u0000" + to.id() : to.id() + "\u0000" + from.id();
            if (!pairs.add(pair)) issue(issues, "duplicate-connection",
                    "Duplicate connection between " + from.label() + " and " + to.label() + ".");
            if (from.floor() == to.floor() && edge.type() != NavigationEdgeType.WALK)
                issue(issues, "invalid-edge-type", "Same-floor connection between " + from.label() + " and "
                        + to.label() + " must be Walk.");
            if (from.floor() != to.floor()) {
                NavigationNodeType required = edge.type() == NavigationEdgeType.LIFT
                        ? NavigationNodeType.LIFT : NavigationNodeType.STAIRCASE;
                if (edge.type() == NavigationEdgeType.WALK || from.type() != required || to.type() != required) {
                    issue(issues, "invalid-floor-transition", "Invalid " + edge.type().label().toLowerCase()
                            + " transition between " + from.label() + " (Floor " + from.floor() + ") and "
                            + to.label() + " (Floor " + to.floor() + ").");
                } else if (edge.verified() && from.verified() && to.verified()) {
                    validFloorTransition = true;
                }
            }
            if (edge.distanceMeters() != null
                    && (!Double.isFinite(edge.distanceMeters()) || edge.distanceMeters() <= 0))
                issue(issues, "invalid-distance", "Connection " + edge.id() + " has an invalid distance.");
            if (edge.distanceMeters() != null && !edge.distanceVerified())
                issue(issues, "unverified-distance", "Distance for connection " + edge.id() + " is not verified.");
            if (edge.distanceVerified() && edge.distanceMeters() == null)
                issue(issues, "missing-distance", "Connection " + edge.id()
                        + " is marked distance-verified but has no distance.");
            if (edge.accessible() && !edge.accessibilityVerified())
                issue(issues, "unverified-accessibility", "Accessibility is marked for connection " + edge.id()
                        + " but has not been verified.");
            if (!edge.verified()) issue(issues, "unverified-connection", "Connection is not verified: "
                    + from.label() + " to " + to.label() + ".");
        }
        if (graph.nodes().stream().anyMatch(a -> a.floor() == 4) && graph.nodes().stream().anyMatch(a -> a.floor() == 5)
                && !validFloorTransition)
            issue(issues, "missing-floor-transition", "No valid verified connection between Floor 4 and Floor 5.");

        Map<Integer, List<NavigationRoomLink>> linksByRoom = new HashMap<>();
        Map<Integer, Room> roomsById = new HashMap<>();
        for (Room room : activeRooms) {
            if (room.floor() == 4 || room.floor() == 5) roomsById.put(room.id(), room);
        }
        for (NavigationRoomLink link : graph.roomLinks()) {
            linksByRoom.computeIfAbsent(link.roomId(), ignored -> new ArrayList<>()).add(link);
            Room room = roomsById.get(link.roomId());
            NavigationNode node = nodes.get(link.nodeId());
            if (room == null) issue(issues, "unknown-room", "Room link refers to an inactive, missing, or unsupported room ID "
                    + link.roomId() + ".");
            else if (node == null) issue(issues, "missing-room-node", "Room " + room.name()
                    + " is linked to a missing navigation node.");
            else if (room.floor() != node.floor()) issue(issues, "room-floor-mismatch",
                    "Room " + room.name() + " and its linked corridor node are on different floors.");
        }
        for (Room room : roomsById.values()) {
            List<NavigationRoomLink> links = linksByRoom.getOrDefault(room.id(), List.of());
            if (links.isEmpty()) issue(issues, "missing-room-link", "Room " + room.name()
                    + " (Floor " + room.floor() + ") is not connected to a corridor.");
            else if (links.size() > 1) issue(issues, "duplicate-room-link", "Room " + room.name()
                    + " has multiple corridor links.");
        }

        if (!graph.nodes().isEmpty()) {
            Set<String> reached = new HashSet<>();
            List<String> frontier = new ArrayList<>();
            NavigationNode firstNode = graph.nodes().stream().filter(NavigationNode::verified).findFirst().orElse(null);
            if (firstNode == null) {
                for (NavigationNode node : graph.nodes()) issue(issues, "disconnected-node",
                        "Navigation node " + node.label() + " (Floor " + node.floor() + ") is disconnected.");
            } else {
                reached.add(firstNode.id());
                frontier.add(firstNode.id());
                while (!frontier.isEmpty()) {
                    String current = frontier.remove(frontier.size() - 1);
                    for (NavigationEdge edge : graph.edges()) {
                        if (!edge.verified() || !nodes.containsKey(edge.fromNodeId()) || !nodes.containsKey(edge.toNodeId())
                                || !nodes.get(edge.fromNodeId()).verified() || !nodes.get(edge.toNodeId()).verified()) continue;
                        NavigationNode next = null;
                        if (edge.fromNodeId().equals(current)) next = nodes.get(edge.toNodeId());
                        else if (edge.toNodeId().equals(current)) next = nodes.get(edge.fromNodeId());
                        if (next != null && reached.add(next.id())) frontier.add(next.id());
                    }
                }
                for (NavigationNode node : graph.nodes()) {
                    if (!reached.contains(node.id())) issue(issues, "disconnected-node",
                            "Navigation node " + node.label() + " (Floor " + node.floor() + ") is disconnected.");
                }
            }
        }
        return new NavigationValidation(issues);
    }

    private record QueueItem(String nodeId, double cost) { }
    private record Previous(String nodeId, NavigationEdge edge) { }

    public static NavigationResult calculateRoute(NavigationGraph graph, List<Room> activeRooms,
                                                  int fromRoomId, int toRoomId, boolean accessible) {
        Room fromRoom = activeRooms.stream().filter(r -> r.id() == fromRoomId && r.active()).findFirst().orElse(null);
        Room toRoom = activeRooms.stream().filter(r -> r.id() == toRoomId && r.active()).findFirst().orElse(null);
        if (fromRoom == null || toRoom == null) return new NavigationResult(null, "Choose two active rooms.");
        if (graph.nodes().stream().noneMatch(node -> node.floor() == fromRoom.floor()))
            return new NavigationResult(null, "No navigation nodes configured for Floor " + fromRoom.floor()
                    + ". Ask an authorized coordinator to configure the floor navigation graph.");
        if (graph.nodes().stream().noneMatch(node -> node.floor() == toRoom.floor()))
            return new NavigationResult(null, "No navigation nodes configured for Floor " + toRoom.floor()
                    + ". Ask an authorized coordinator to configure the floor navigation graph.");
        Map<Integer, NavigationRoomLink> links = new HashMap<>();
        graph.roomLinks().forEach(link -> links.put(link.roomId(), link));
        NavigationRoomLink fromLink = links.get(fromRoomId);
        NavigationRoomLink toLink = links.get(toRoomId);
        if (fromLink == null) return new NavigationResult(null, "Starting room " + fromRoom.name()
                + " is not connected to a corridor. Ask a coordinator to link it to a verified corridor node.");
        if (toLink == null) return new NavigationResult(null, "Destination room " + toRoom.name()
                + " is not connected to a corridor. Ask a coordinator to link it to a verified corridor node.");
        if (fromRoom.floor() != toRoom.floor() && graph.edges().stream().noneMatch(e -> e.verified()
                && (e.type() == NavigationEdgeType.STAIRCASE || e.type() == NavigationEdgeType.LIFT)
                && floorTransition(e, graph.nodes()))) {
            return new NavigationResult(null, "No valid verified connection between Floor " + fromRoom.floor()
                    + " and Floor " + toRoom.floor() + ". Ask a coordinator to configure and verify a lift or staircase.");
        }
        if (!graph.published()) return new NavigationResult(null,
                "Navigation graph failed validation or has not been published. Ask an authorized coordinator to validate and publish it.");
        NavigationValidation validation = validate(graph, activeRooms);
        if (!validation.valid()) return new NavigationResult(null, "Navigation graph failed validation: "
                + validation.issues().get(0).message() + " Ask a coordinator to resolve the diagnostics.");
        if (accessible && graph.edges().stream().noneMatch(e -> e.verified() && e.accessible()
                && e.accessibilityVerified()))
            return new NavigationResult(null, "No verified accessible route is configured. Ask a coordinator to verify accessible paths.");

        Map<String, NavigationNode> nodes = new HashMap<>();
        graph.nodes().forEach(n -> nodes.put(n.id(), n));
        String start = fromLink.nodeId();
        String finish = toLink.nodeId();
        Map<String, Double> costs = new HashMap<>();
        Map<String, Previous> previous = new HashMap<>();
        PriorityQueue<QueueItem> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueItem::cost));
        boolean measuredDistances = graph.edges().stream().filter(NavigationEdge::verified)
                .filter(edge -> !accessible || edge.accessible() && edge.accessibilityVerified())
                .allMatch(edge -> edge.distanceMeters() != null && edge.distanceVerified());
        costs.put(start, 0d);
        queue.add(new QueueItem(start, 0));
        while (!queue.isEmpty()) {
            QueueItem current = queue.remove();
            if (current.cost() != costs.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY)) continue;
            if (current.nodeId().equals(finish)) break;
            for (NavigationEdge edge : graph.edges()) {
                if (!edge.verified() || (accessible && (!edge.accessible() || !edge.accessibilityVerified()))) continue;
                String next = edge.fromNodeId().equals(current.nodeId()) ? edge.toNodeId()
                        : edge.toNodeId().equals(current.nodeId()) ? edge.fromNodeId() : null;
                if (next == null || !nodes.containsKey(next) || !nodes.get(next).verified()) continue;
                double weight = measuredDistances ? edge.distanceMeters() : 1d;
                double candidate = current.cost() + weight;
                if (candidate < costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    costs.put(next, candidate);
                    previous.put(next, new Previous(current.nodeId(), edge));
                    queue.add(new QueueItem(next, candidate));
                }
            }
        }
        if (!costs.containsKey(finish)) return new NavigationResult(null,
                accessible ? "Destination is unreachable using verified accessible paths."
                        : "Destination is unreachable using verified corridor connections.");
        List<NavigationNode> pathNodes = new ArrayList<>();
        List<NavigationEdge> pathEdges = new ArrayList<>();
        String cursor = finish;
        pathNodes.add(nodes.get(cursor));
        while (!cursor.equals(start)) {
            Previous step = previous.get(cursor);
            if (step == null) return new NavigationResult(null, "Destination is unreachable using verified corridor connections.");
            pathEdges.add(step.edge());
            cursor = step.nodeId();
            pathNodes.add(nodes.get(cursor));
        }
        java.util.Collections.reverse(pathNodes);
        java.util.Collections.reverse(pathEdges);
        Double distance = pathEdges.stream().allMatch(e -> e.distanceMeters() != null && e.distanceVerified())
                ? pathEdges.stream().mapToDouble(NavigationEdge::distanceMeters).sum() : null;
        return new NavigationResult(new NavigationPath(pathNodes, pathEdges, distance, accessible), null);
    }

    private static boolean floorTransition(NavigationEdge edge, List<NavigationNode> nodes) {
        NavigationNode from = nodes.stream().filter(n -> n.id().equals(edge.fromNodeId())).findFirst().orElse(null);
        NavigationNode to = nodes.stream().filter(n -> n.id().equals(edge.toNodeId())).findFirst().orElse(null);
        return from != null && to != null && from.floor() != to.floor()
                && from.verified() && to.verified()
                && (edge.type() == NavigationEdgeType.LIFT && from.type() == NavigationNodeType.LIFT
                && to.type() == NavigationNodeType.LIFT
                || edge.type() == NavigationEdgeType.STAIRCASE && from.type() == NavigationNodeType.STAIRCASE
                && to.type() == NavigationNodeType.STAIRCASE);
    }

    private static void issue(List<NavigationIssue> issues, String code, String message) {
        issues.add(new NavigationIssue(code, message));
    }
}
