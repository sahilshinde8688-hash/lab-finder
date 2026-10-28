package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.NavigationEdge;
import com.sakec.smartspace.model.NavigationEdgeType;
import com.sakec.smartspace.model.NavigationGraph;
import com.sakec.smartspace.model.NavigationNode;
import com.sakec.smartspace.model.NavigationNodeType;
import com.sakec.smartspace.model.NavigationRoomLink;
import com.sakec.smartspace.util.Db;
import java.sql.Connection;
import java.util.List;

public class NavigationDao {
    private static final Db.RowMapper<NavigationNode> NODE_MAPPER = rs ->
            new NavigationNode(rs.getString("node_id"), rs.getInt("floor_number"),
                    NavigationNodeType.valueOf(rs.getString("node_type")), rs.getString("label"),
                    rs.getDouble("x"), rs.getDouble("y"), rs.getInt("verified") == 1);
    private static final Db.RowMapper<NavigationEdge> EDGE_MAPPER = rs -> {
        double distance = rs.getDouble("distance_meters");
        Double distanceValue = rs.wasNull() ? null : distance;
        return new NavigationEdge(rs.getString("edge_id"), rs.getString("from_node_id"),
                rs.getString("to_node_id"), NavigationEdgeType.valueOf(rs.getString("connection_type")),
                distanceValue, rs.getInt("distance_verified") == 1, rs.getInt("accessible") == 1,
                rs.getInt("accessibility_verified") == 1, rs.getInt("verified") == 1);
    };
    private static final Db.RowMapper<NavigationRoomLink> ROOM_LINK_MAPPER = rs ->
            new NavigationRoomLink(rs.getInt("room_id"), rs.getString("node_id"));

    public NavigationGraph draft() {
        return Db.inTransaction(connection -> {
            Long draftId = revisionId(connection, "DRAFT");
            if (draftId == null) {
                draftId = Db.insert(connection, "INSERT INTO navigation_revisions(status) VALUES ('DRAFT')");
                Long publishedId = revisionId(connection, "PUBLISHED");
                if (publishedId != null) {
                    Db.update(connection, "INSERT INTO navigation_nodes SELECT ?, node_id, floor_number, node_type, "
                            + "label, x, y, verified FROM navigation_nodes WHERE revision_id = ?", draftId, publishedId);
                    Db.update(connection, "INSERT INTO navigation_edges SELECT ?, edge_id, from_node_id, to_node_id, "
                            + "connection_type, distance_meters, distance_verified, accessible, accessibility_verified, verified "
                            + "FROM navigation_edges WHERE revision_id = ?", draftId, publishedId);
                    Db.update(connection, "INSERT INTO navigation_room_links SELECT ?, room_id, node_id "
                            + "FROM navigation_room_links WHERE revision_id = ?", draftId, publishedId);
                }
            }
            return graph(connection, draftId, false);
        });
    }

    public NavigationGraph published() {
        Long id = Db.queryOne("SELECT revision_id FROM navigation_revisions WHERE status='PUBLISHED' "
                        + "ORDER BY revision_id DESC LIMIT 1", rs -> rs.getLong(1));
        return id == null ? NavigationGraph.empty() : graph(id, true);
    }

    private NavigationGraph graph(long revisionId, boolean published) {
        return Db.inTransaction(connection -> graph(connection, revisionId, published));
    }

    private NavigationGraph graph(Connection connection, long revisionId, boolean published) {
        List<NavigationNode> nodes = Db.query(connection, "SELECT * FROM navigation_nodes WHERE revision_id=? "
                + "ORDER BY floor_number, label", NODE_MAPPER, revisionId);
        List<NavigationEdge> edges = Db.query(connection, "SELECT * FROM navigation_edges WHERE revision_id=? "
                + "ORDER BY edge_id", EDGE_MAPPER, revisionId);
        List<NavigationRoomLink> links = Db.query(connection, "SELECT * FROM navigation_room_links WHERE revision_id=? "
                + "ORDER BY room_id", ROOM_LINK_MAPPER, revisionId);
        return new NavigationGraph(revisionId, published, nodes, edges, links);
    }

    public void saveNode(long revisionId, NavigationNode node) {
        Db.update("INSERT INTO navigation_nodes(revision_id,node_id,floor_number,node_type,label,x,y,verified) "
                        + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(revision_id,node_id) DO UPDATE SET "
                        + "floor_number=excluded.floor_number,node_type=excluded.node_type,label=excluded.label,"
                        + "x=excluded.x,y=excluded.y,verified=excluded.verified",
                revisionId, node.id(), node.floor(), node.type(), node.label(), node.x(), node.y(), node.verified());
    }

    public void deleteNode(long revisionId, String nodeId) {
        Db.update("DELETE FROM navigation_nodes WHERE revision_id=? AND node_id=?", revisionId, nodeId);
    }

    public void saveEdge(long revisionId, NavigationEdge edge) {
        String from = edge.fromNodeId().compareTo(edge.toNodeId()) < 0 ? edge.fromNodeId() : edge.toNodeId();
        String to = edge.fromNodeId().compareTo(edge.toNodeId()) < 0 ? edge.toNodeId() : edge.fromNodeId();
        Db.update("INSERT INTO navigation_edges(revision_id,edge_id,from_node_id,to_node_id,connection_type,"
                        + "distance_meters,distance_verified,accessible,accessibility_verified,verified) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(revision_id,edge_id) DO UPDATE SET from_node_id=excluded.from_node_id,"
                        + "to_node_id=excluded.to_node_id,"
                        + "connection_type=excluded.connection_type,distance_meters=excluded.distance_meters,"
                        + "distance_verified=excluded.distance_verified,"
                        + "accessible=excluded.accessible,accessibility_verified=excluded.accessibility_verified,"
                        + "verified=excluded.verified",
                revisionId, edge.id(), from, to, edge.type(), edge.distanceMeters(), edge.distanceVerified(), edge.accessible(),
                edge.accessibilityVerified(), edge.verified());
    }

    public void deleteEdge(long revisionId, String edgeId) {
        Db.update("DELETE FROM navigation_edges WHERE revision_id=? AND edge_id=?", revisionId, edgeId);
    }

    public void saveRoomLink(long revisionId, NavigationRoomLink link) {
        Db.inTransaction(connection -> {
            Db.update(connection, "DELETE FROM navigation_room_links WHERE revision_id=? AND room_id=?",
                    revisionId, link.roomId());
            Db.update(connection, "INSERT INTO navigation_room_links(revision_id,room_id,node_id) VALUES (?,?,?)",
                    revisionId, link.roomId(), link.nodeId());
            return null;
        });
    }

    public void deleteRoomLink(long revisionId, int roomId) {
        Db.update("DELETE FROM navigation_room_links WHERE revision_id=? AND room_id=?", revisionId, roomId);
    }

    public void publish(NavigationGraph expectedDraft) {
        Db.inTransaction(connection -> {
            NavigationGraph currentDraft = graph(connection, expectedDraft.revisionId(), false);
            if (!currentDraft.equals(expectedDraft))
                throw new IllegalStateException("The navigation draft changed during validation. Validate it again.");
            Db.update(connection, "UPDATE navigation_revisions SET status='ARCHIVED' WHERE status='PUBLISHED'");
            int updated = Db.update(connection, "UPDATE navigation_revisions SET status='PUBLISHED', "
                    + "published_at=? "
                    + "WHERE revision_id=? AND status='DRAFT'",
                    com.sakec.smartspace.util.Dt.toDb(java.time.LocalDateTime.now()), expectedDraft.revisionId());
            if (updated != 1) throw new IllegalStateException("The navigation draft is no longer available.");
            return null;
        });
    }

    private Long revisionId(Connection connection, String status) {
        List<Long> revisions = Db.query(connection, "SELECT revision_id FROM navigation_revisions WHERE status=? "
                + "ORDER BY revision_id DESC LIMIT 1", rs -> rs.getLong(1), status);
        return revisions.isEmpty() ? null : revisions.get(0);
    }
}
