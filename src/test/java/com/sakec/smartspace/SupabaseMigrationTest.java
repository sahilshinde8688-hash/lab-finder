package com.sakec.smartspace;

import com.sakec.smartspace.util.Db;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseMigrationTest {

    @Test
    void supabaseSchemaFileIsPresentAndNonEmpty() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/database/schema-supabase.sql")) {
            assertNotNull(in, "schema-supabase.sql should exist in resources");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS users"));
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS rooms"));
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS timetable"));
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS bookings"));
            assertTrue(sql.contains("ENABLE ROW LEVEL SECURITY"));
        }
    }

    @Test
    void isPostgresDetection() {
        assertTrue(Db.isPostgres());
    }

    @Test
    void liveSupabaseConnectionTest() throws Exception {
        try (java.sql.Connection c = Db.open()) {
            assertNotNull(c, "Supabase connection should be opened successfully");
            try (java.sql.Statement s = c.createStatement();
                 java.sql.ResultSet rs = s.executeQuery("SELECT current_database(), current_user")) {
                assertTrue(rs.next());
                System.out.println("Connected to Supabase DB: " + rs.getString(1) + " as user: " + rs.getString(2));
            }
        }
    }

    @Test
    void liveSupabaseSchemaAndDataFetch() throws Exception {
        Db.initSchema();
        com.sakec.smartspace.dao.RoomDao roomDao = new com.sakec.smartspace.dao.RoomDao();
        var rooms = roomDao.listActive();
        assertNotNull(rooms);
        System.out.println("Successfully fetched " + rooms.size() + " rooms from Supabase!");

        com.sakec.smartspace.dao.UserDao userDao = new com.sakec.smartspace.dao.UserDao();
        var allUsers = Db.query("SELECT user_id, full_name, institutional_email, role FROM users", rs ->
            rs.getLong("user_id") + ": " + rs.getString("full_name") + " (" + rs.getString("institutional_email") + ")");
        System.out.println("Fetched " + allUsers.size() + " users from Supabase: " + allUsers);

        com.sakec.smartspace.dao.TimetableDao timetableDao = new com.sakec.smartspace.dao.TimetableDao();
        var timetable = timetableDao.listAll();
        System.out.println("Fetched " + timetable.size() + " timetable slots from Supabase!");
    }

    @Test
    void inspectDemoCoordinator() throws Exception {
        var user = new com.sakec.smartspace.dao.UserDao().findByEmail("demo.coordinator2@sakec.ac.in");
        org.junit.jupiter.api.Assertions.assertNotNull(user);
        org.junit.jupiter.api.Assertions.assertEquals(com.sakec.smartspace.model.Role.COORDINATOR, user.role());
    }
}
