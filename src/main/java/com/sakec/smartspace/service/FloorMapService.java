package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.FloorMapDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.FloorMapRegion;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.util.AppException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FloorMapService {
    private final FloorMapDao maps = new FloorMapDao();
    private final RoomDao rooms = new RoomDao();

    public List<FloorMapRegion> regions(int floor, boolean includeDrafts) {
        if (includeDrafts) Permissions.require(Role.COORDINATOR);
        List<FloorMapRegion> published = maps.published(floor);
        if (!includeDrafts) return published;
        Map<String, FloorMapRegion> combined = new LinkedHashMap<>();
        published.forEach(region -> combined.put(region.key(), region));
        maps.drafts(floor).forEach(region -> combined.put(region.key(), region));
        return new ArrayList<>(combined.values());
    }

    public void saveDraft(FloorMapRegion region) {
        Permissions.require(Role.COORDINATOR);
        validate(region);
        maps.saveDraft(region);
    }

    public void publishDraft(int floor, String key) {
        Permissions.require(Role.COORDINATOR);
        FloorMapRegion draft = maps.drafts(floor).stream().filter(region -> region.key().equals(key))
                .findFirst().orElseThrow(() -> new AppException("The calibration draft no longer exists."));
        validate(draft);
        try {
            maps.publishDraft(floor, key);
        } catch (IllegalArgumentException error) {
            throw new AppException(error.getMessage());
        }
    }

    private void validate(FloorMapRegion region) {
        if (region == null || (region.floor() != 4 && region.floor() != 5)
                || region.key() == null || region.key().isBlank()
                || region.number() == null || region.number().isBlank() || region.number().length() > 30
                || region.type() == null || region.type().isBlank() || region.type().length() > 40) {
            throw new AppException("Enter a valid map label, type, and floor.");
        }
        if (!Double.isFinite(region.x()) || !Double.isFinite(region.y())
                || !Double.isFinite(region.width()) || !Double.isFinite(region.height())
                || region.x() < 0 || region.y() < 0 || region.width() <= 0 || region.height() <= 0
                || region.x() + region.width() > 1 || region.y() + region.height() > 1) {
            throw new AppException("Map coordinates must describe a non-empty rectangle inside normalized 0.0–1.0 image bounds.");
        }
        if (region.roomId() != null) {
            var room = rooms.findById(region.roomId());
            if (room == null || room.floor() != region.floor() || !room.active()) {
                throw new AppException("Choose an active room on the same floor as the map region.");
            }
        }
    }
}
