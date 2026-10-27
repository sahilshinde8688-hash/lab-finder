package com.sakec.smartspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.util.RoomLabelMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomLabelMatcherTest {
    private static final List<Room> ROOMS = List.of(
            new Room(1, "SAMPLE-401", 4, RoomType.CLASSROOM, 60, "", true, true),
            new Room(2, "Room 401", 4, RoomType.CLASSROOM, 30, "", true, false),
            new Room(3, "SAMPLE-402", 4, RoomType.LABORATORY, 40, "", true, true),
            new Room(4, "SAMPLE-401", 5, RoomType.CLASSROOM, 60, "", true, true));

    @Test
    void requiresExactRoomNumberAndCompatibleTypeOnSelectedFloor() {
        assertEquals(List.of(ROOMS.get(0), ROOMS.get(1)),
                RoomLabelMatcher.matches(ROOMS, 4, "401", "Classroom"));
        assertEquals(List.of(), RoomLabelMatcher.matches(ROOMS, 4, "401", "Laboratory"));
        assertEquals(List.of(ROOMS.get(3)), RoomLabelMatcher.matches(ROOMS, 5, "401", "Classroom"));
    }

    @Test
    void rejectsAmbiguousAndNonRoomLabels() {
        assertEquals(2, RoomLabelMatcher.matches(ROOMS, 4, "401", "Classroom").size());
        assertEquals(List.of(), RoomLabelMatcher.matches(ROOMS, 4, "Library", "Library"));
    }
}
