package com.sakec.smartspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.dao.NavigationDao;
import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.NavigationEdge;
import com.sakec.smartspace.model.NavigationEdgeType;
import com.sakec.smartspace.model.NavigationNode;
import com.sakec.smartspace.model.NavigationNodeType;
import com.sakec.smartspace.model.NavigationRoomLink;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.SearchCriteria;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.PasswordHasher;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.service.AuthService;
import com.sakec.smartspace.service.AvailabilityService;
import com.sakec.smartspace.service.BookingService;
import com.sakec.smartspace.service.NavigationService;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.RoomLabelMatcher;
import com.sakec.smartspace.util.Validators;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs against a throw-away SQLite file. Run with: mvn test */
class BackendFlowTest {
    @BeforeAll
    static void setUp() {
        File f = new File(System.getProperty("java.io.tmpdir"), "smartspace-test-" + System.nanoTime() + ".db");
        f.deleteOnExit();
        System.setProperty("smartspace.db.url", "jdbc:sqlite:" + f.getAbsolutePath());
        Db.initSchema();
    }

    @AfterAll
    static void tearDown() {
        Db.clearPool();
        System.clearProperty("smartspace.db.url");
    }

    @Test
    void passwordHashingRoundTrip() {
        String h = PasswordHasher.hash("Correct-horse-1");
        assertTrue(PasswordHasher.verify("Correct-horse-1", h));
        assertFalse(PasswordHasher.verify("wrong-password-1", h));
        assertFalse(h.contains("Correct-horse-1"));
    }

    @Test
    void emailAndPasswordRules() {
        assertTrue(Validators.isAllowedDomain("a.b@sakec.ac.in"));
        assertFalse(Validators.isAllowedDomain("a.b@gmail.com"));
        assertFalse(Validators.isEmailFormatValid("not-an-email"));
        assertEquals(null, Validators.checkPassword("longenough123"));
        assertFalse(Validators.checkPassword("short1") == null);
    }

    @Test
    void overlapRulesAreHalfOpen() {
        LocalDateTime t = LocalDateTime.of(2030, 1, 1, 10, 0);
        assertTrue(Dt.overlaps(t, t.plusHours(1), t.plusMinutes(30), t.plusHours(2)));
        assertFalse(Dt.overlaps(t, t.plusHours(1), t.plusHours(1), t.plusHours(2))); // back-to-back is fine
    }

    @Test
    void unauthenticatedUsersAreRejected() {
        assertThrows(AppException.class, () -> new BookingService().mine());
        assertThrows(AppException.class, () -> new AuthService().listUsers());
        assertThrows(AppException.class, () -> new NavigationService().draft());
    }

    @Test
    void floorInventoryIsProvisionedIdempotently() {
        Db.initSchema();
        List<Room> rooms = new RoomDao().listActive().stream()
                .filter(room -> room.floor() == 4 || room.floor() == 5)
                .toList();

        assertEquals(25, rooms.size());
        assertEquals(8, rooms.stream().filter(room -> room.floor() == 4).count());
        assertEquals(17, rooms.stream().filter(room -> room.floor() == 5).count());
        assertEquals(Set.of(
                "Classroom 401", "Classroom 409", "Lab 402", "Lab 415", "Lab 418", "Lab 419", "Lab 420", "Lab 422",
                "Classroom 502", "Classroom 506", "Classroom 507", "Classroom 508 (PG Classroom)",
                "Classroom 509", "Classroom 513", "Classroom 514", "Classroom 515", "Classroom 519",
                "Lab 501", "Lab 503", "Lab 504", "Lab 518", "Lab 520", "Lab 521", "Lab 523", "Lab 524"),
                rooms.stream().map(Room::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rooms.stream().allMatch(room -> room.capacity() == 60 && !room.sample()));
        assertEquals(RoomType.CLASSROOM, rooms.stream()
                .filter(room -> room.name().equals("Classroom 508 (PG Classroom)"))
                .findFirst().orElseThrow().type());
        assertEquals(1, RoomLabelMatcher.matches(rooms, 5, "508", "pg classroom").size());
    }

    @Test
    void navigationDraftPersistsNodesEdgesAndRoomLinks() {
        NavigationDao dao = new NavigationDao();
        long revision = dao.draft().revisionId();
        Room room = new RoomDao().listActive().stream().filter(candidate -> candidate.floor() == 4)
                .findFirst().orElseThrow();
        dao.saveNode(revision, new NavigationNode("test-node-a", 4, NavigationNodeType.CORRIDOR,
                "Test corridor A", .2, .3, true));
        dao.saveNode(revision, new NavigationNode("test-node-b", 4, NavigationNodeType.JUNCTION,
                "Test junction B", .4, .3, true));
        dao.saveEdge(revision, new NavigationEdge("test-edge", "test-node-b", "test-node-a",
                NavigationEdgeType.WALK, 8.5, true, true, true, true));
        dao.saveRoomLink(revision, new NavigationRoomLink(room.id(), "test-node-a"));

        var stored = dao.draft();

        assertEquals(2, stored.nodes().size());
        assertEquals(8.5, stored.edges().get(0).distanceMeters());
        assertEquals(List.of(new NavigationRoomLink(room.id(), "test-node-a")), stored.roomLinks());
    }

    @Test
    void coordinatorCanDeleteUserCompletely() {
        AuthService auth = new AuthService();
        UserDao users = new UserDao();
        long coordId = users.create("Test Coordinator", "coord.delete@sakec.ac.in",
                PasswordHasher.hash("CoordinatorPassword1!"), Role.COORDINATOR, "IT", false, false);
        long studentId = users.create("Student To Delete", "student.delete@sakec.ac.in",
                PasswordHasher.hash("StudentPassword1!"), Role.STUDENT, "IT", false, false);

        User coord = users.findById((int) coordId);
        Session.start(coord);
        try {
            // Cannot delete self
            assertThrows(AppException.class, () -> auth.deleteUser((int) coordId));

            // Delete student completely
            assertNotNull(users.findById((int) studentId));
            auth.deleteUser((int) studentId);
            assertNull(users.findById((int) studentId));
        } finally {
            Session.clear();
        }
    }
}
