package com.sakec.smartspace.app;

import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.dao.TimetableDao;
import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.PasswordHasher;
import com.sakec.smartspace.util.Config;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Log;
import com.sakec.smartspace.util.Validators;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * First-run helpers. Nothing here runs unless you ask for it:
 *   --seed                                   (or seed.dev=true)  sample users, rooms and timetable
 *   --create-coordinator email "Full Name"   a real coordinator account with a one-time temporary password
 * Temporary passwords are printed once to the console and must be changed at first sign-in.
 */
public final class Bootstrap {
    private Bootstrap() { }

    public static void handle(String[] args) {
        boolean seed = Config.getBool("seed.dev", false);
        for (int i = 0; i < args.length; i++) {
            if ("--seed".equals(args[i])) seed = true;
            if ("--create-coordinator".equals(args[i]) && i + 2 < args.length) {
                createCoordinator(args[i + 1], args[i + 2]);
            }
        }
        if (seed) seedSampleData();
    }

    static void createCoordinator(String email, String name) {
        String err = Validators.checkEmail(email);
        if (err != null) { System.out.println("Cannot create coordinator: " + err); return; }
        UserDao users = new UserDao();
        if (users.findByEmail(email) != null) { System.out.println("An account for " + email + " already exists."); return; }
        String temp = PasswordHasher.temporaryPassword();
        users.create(name, email, PasswordHasher.hash(temp), Role.COORDINATOR, "Timetable Cell", true);
        System.out.println("Coordinator created: " + email + "   TEMPORARY password (shown once): " + temp);
    }

    static void seedSampleData() {
        UserDao users = new UserDao();
        if (users.count() > 0) { Log.info("Seeding skipped: database already has users."); return; }
        Db.runScript("/database/seed.sql");

        String domain = Validators.allowedDomains().get(0);
        String temp = System.getenv("SMARTSPACE_DEV_PASSWORD");
        if (temp == null || Validators.checkPassword(temp) != null) temp = PasswordHasher.temporaryPassword();
        String hash = PasswordHasher.hash(temp);

        users.create("Sample Coordinator", "coordinator.sample@" + domain, hash, Role.COORDINATOR, "Timetable Cell", true);
        String[][] teachers = {{"Sample Teacher One", "teacher1.sample", "Computer Engineering"},
                {"Sample Teacher Two", "teacher2.sample", "Information Technology"},
                {"Sample Teacher Three", "teacher3.sample", "Electronics"}};
        for (String[] t : teachers) users.create(t[0], t[1] + "@" + domain, hash, Role.TEACHER, t[2], true);
        users.create("Sample Student One", "student1.sample@" + domain, hash, Role.STUDENT, "Computer Engineering", true);
        users.create("Sample Student Two", "student2.sample@" + domain, hash, Role.STUDENT, "Information Technology", true);

        // Sample weekly timetable, clearly flagged is_sample=1. NOT the real college timetable.
        List<Room> rooms = new RoomDao().listActive();
        List<User> faculty = users.listByRole(Role.TEACHER);
        String[] subjects = {"Sample Mathematics", "Sample Data Structures", "Sample Physics Lab", "Sample Networks",
                "Sample Databases", "Sample Electronics"};
        String[] classes = {"SAMPLE-A", "SAMPLE-B", "SAMPLE-C"};
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        TimetableDao tt = new TimetableDao();
        int k = 0;
        for (int day = 0; day < 5; day++) {
            for (int slot = 0; slot < 4; slot++) {
                Room room = rooms.get((day * 5 + slot * 3) % rooms.size());
                User teacher = faculty.get((day + slot) % faculty.size());
                LocalTime start = LocalTime.of(9 + slot * 2, 0);
                LocalDate date = monday.plusDays(day);
                try {
                    tt.insert(room.id(), subjects[k % subjects.length], classes[(day + slot) % classes.length], teacher.id(),
                            date.atTime(start), date.atTime(start.plusHours(1).plusMinutes(30)), "WEEKLY", monday.plusWeeks(15));
                } catch (RuntimeException e) {
                    Log.warn("Skipped one sample timetable row");
                }
                k++;
            }
        }
        Db.update("UPDATE timetable SET is_sample = 1");
        System.out.println("=== SAMPLE DATA CREATED (development only) ===");
        System.out.println("Accounts: coordinator.sample@" + domain + ", teacher1.sample@" + domain
                + ", student1.sample@" + domain + " (and teacher2/3, student2)");
        System.out.println("TEMPORARY password for all sample accounts (shown once): " + temp);
        System.out.println("You will be asked to change it at first sign-in.");
    }
}
