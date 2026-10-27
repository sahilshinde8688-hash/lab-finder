package com.sakec.smartspace.dao;

import com.sakec.smartspace.model.FloorMapRegion;
import com.sakec.smartspace.util.Db;
import java.sql.Connection;
import java.util.List;

public class FloorMapDao {
    private static final String COLUMNS = "floor_number, region_key, map_number, map_type, x, y, width, height, room_id";
    private static final Db.RowMapper<FloorMapRegion> MAPPER = rs -> {
        int roomId = rs.getInt("room_id");
        boolean roomIdIsNull = rs.wasNull();
        return new FloorMapRegion(rs.getInt("floor_number"), rs.getString("region_key"), rs.getString("map_number"),
                rs.getString("map_type"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("width"),
                rs.getDouble("height"), roomIdIsNull ? null : roomId);
    };

    public List<FloorMapRegion> published(int floor) {
        return Db.query("SELECT " + COLUMNS + " FROM floor_map_regions WHERE floor_number = ?", MAPPER, floor);
    }

    public List<FloorMapRegion> drafts(int floor) {
        return Db.query("SELECT " + COLUMNS + " FROM floor_map_region_drafts WHERE floor_number = ?", MAPPER, floor);
    }

    public void saveDraft(FloorMapRegion region) {
        Db.update("INSERT INTO floor_map_region_drafts(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(floor_number, region_key) DO UPDATE SET map_number=excluded.map_number, "
                        + "map_type=excluded.map_type, x=excluded.x, y=excluded.y, width=excluded.width, "
                        + "height=excluded.height, room_id=excluded.room_id",
                region.floor(), region.key(), region.number(), region.type(), region.x(), region.y(),
                region.width(), region.height(), region.roomId());
    }

    public void publishDraft(int floor, String key) {
        Db.inTransaction(connection -> {
            List<FloorMapRegion> drafts = Db.query(connection,
                    "SELECT " + COLUMNS + " FROM floor_map_region_drafts WHERE floor_number = ? AND region_key = ?",
                    MAPPER, floor, key);
            if (drafts.isEmpty()) throw new IllegalStateException("The calibration draft no longer exists.");
            FloorMapRegion region = drafts.get(0);
            if (region.roomId() != null) {
                long duplicate = Db.query(connection,
                        "SELECT COUNT(*) FROM floor_map_regions WHERE floor_number = ? AND room_id = ? AND region_key <> ?",
                        rs -> rs.getLong(1), floor, region.roomId(), key).get(0);
                if (duplicate > 0) throw new IllegalArgumentException("That room is already assigned to another published map region.");
                long duplicateDraft = Db.query(connection,
                        "SELECT COUNT(*) FROM floor_map_region_drafts WHERE floor_number = ? AND room_id = ? AND region_key <> ?",
                        rs -> rs.getLong(1), floor, region.roomId(), key).get(0);
                if (duplicateDraft > 0) throw new IllegalArgumentException("That room is assigned to another draft map region.");
            }
            Db.update(connection, "INSERT INTO floor_map_regions(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(floor_number, region_key) DO UPDATE SET map_number=excluded.map_number, "
                            + "map_type=excluded.map_type, x=excluded.x, y=excluded.y, width=excluded.width, "
                            + "height=excluded.height, room_id=excluded.room_id",
                    region.floor(), region.key(), region.number(), region.type(), region.x(), region.y(),
                    region.width(), region.height(), region.roomId());
            Db.update(connection, "DELETE FROM floor_map_region_drafts WHERE floor_number = ? AND region_key = ?",
                    floor, key);
            return null;
        });
    }
}
