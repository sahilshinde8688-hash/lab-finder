package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.BookingDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.dao.TimetableDao;
import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.ClassSlot;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Dt;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TimetableService {
    private static final int MAX_OCCURRENCES = 60;
    private final TimetableDao dao = new TimetableDao();
    private final RoomDao rooms = new RoomDao();
    private final UserDao users = new UserDao();
    private final BookingDao bookings = new BookingDao();
    private final NotificationService notifications = new NotificationService();

    public List<TimetableEntry> all() { return dao.listAll(); }

    public TimetableEntry find(int id) { return dao.findById(id); }

    public List<User> faculty() { return users.listByRole(Role.TEACHER); }

    /** Expands entries into concrete occurrences between two dates (inclusive). */
    public static List<ClassSlot> expand(List<TimetableEntry> entries, LocalDate from, LocalDate to) {
        List<ClassSlot> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (TimetableEntry e : entries) {
                if (e.occursOn(d)) out.add(new ClassSlot(e, e.startOn(d), e.endOn(d)));
            }
        }
        out.sort(Comparator.comparing(ClassSlot::start));
        return out;
    }

    public List<ClassSlot> slotsOn(LocalDate day) { return expand(dao.listAll(), day, day); }

    public void save(Integer id, int roomId, String subject, String className, int facultyId, LocalDate date,
                     LocalTime start, LocalTime end, boolean weekly, LocalDate until) {
        Permissions.require(Role.COORDINATOR);
        if (subject == null || subject.isBlank() || subject.length() > 80) throw new AppException("Enter the subject (up to 80 characters).");
        if (className == null || className.isBlank() || className.length() > 60) throw new AppException("Enter the class or batch name.");
        if (date == null || start == null || end == null) throw new AppException("Choose a date, start time and end time.");
        if (!end.isAfter(start)) throw new AppException("The end time must be after the start time.");
        if (Duration.between(start, end).toMinutes() < 15) throw new AppException("A period must be at least 15 minutes long.");
        Room room = rooms.findById(roomId);
        if (room == null || !room.active()) throw new AppException("Choose an active room.");
        User teacher = users.findById(facultyId);
        if (teacher == null || teacher.role() != Role.TEACHER || !teacher.active()) throw new AppException("Choose an active faculty member.");
        if (weekly) {
            if (until == null || until.isBefore(date)) throw new AppException("Choose a repeat-until date on or after the first date.");
            if (until.isAfter(date.plusWeeks(MAX_OCCURRENCES - 1))) throw new AppException("Repeat for at most " + MAX_OCCURRENCES + " weeks at a time.");
        }
        LocalDateTime s = date.atTime(start), e = date.atTime(end);
        TimetableEntry candidate = new TimetableEntry(id == null ? -1 : id, roomId, room.name(), subject.trim(),
                className.trim(), facultyId, teacher.fullName(), s, e, weekly ? "WEEKLY" : "NONE", weekly ? until : null, false);

        String conflict = findConflict(candidate);
        if (conflict != null) throw new AppException(conflict);

        String rule = weekly ? "WEEKLY" : "NONE";
        TimetableEntry old = id == null ? null : dao.findById(id);
        if (id == null) dao.insert(roomId, subject.trim(), className.trim(), facultyId, s, e, rule, weekly ? until : null);
        else dao.update(id, roomId, subject.trim(), className.trim(), facultyId, s, e, rule, weekly ? until : null);

        String when = date.format(Dt.DATE) + ", " + start.format(Dt.TIME) + "–" + end.format(Dt.TIME);
        notifications.notifyUser(facultyId, "Schedule updated",
                subject.trim() + " (" + className.trim() + ") is scheduled in " + room.name() + " from " + when + ".");
        if (old != null && old.facultyId() != facultyId) {
            notifications.notifyUser(old.facultyId(), "Schedule updated", old.subject() + " (" + old.className() + ") was reassigned.");
        }
    }

    public void delete(int id) {
        Permissions.require(Role.COORDINATOR);
        TimetableEntry e = dao.findById(id);
        if (e == null) return;
        dao.delete(id);
        notifications.notifyUser(e.facultyId(), "Schedule updated", e.subject() + " (" + e.className() + ") in "
                + e.roomName() + " was removed from the timetable.");
    }

    /** Returns a readable description of the first conflict, or null when the entry fits. */
    private String findConflict(TimetableEntry c) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate first = c.start().toLocalDate();
        if (c.weekly()) {
            for (LocalDate d = first; !d.isAfter(c.until()); d = d.plusWeeks(1)) dates.add(d);
        } else {
            dates.add(first);
        }
        List<TimetableEntry> others = dao.listAll().stream().filter(o -> o.id() != c.id()).toList();
        LocalDateTime rangeFrom = first.atStartOfDay();
        LocalDateTime rangeTo = dates.get(dates.size() - 1).plusDays(1).atStartOfDay();
        List<Booking> approved = bookings.blocking(rangeFrom, rangeTo);

        for (LocalDate d : dates) {
            LocalDateTime s = c.startOn(d), e = c.endOn(d);
            for (TimetableEntry o : others) {
                if (!o.occursOn(d) || !Dt.overlaps(s, e, o.startOn(d), o.endOn(d))) continue;
                String where = o.subject() + " (" + o.className() + ") on " + d.format(Dt.DATE) + " "
                        + Dt.range(o.startOn(d), o.endOn(d));
                if (o.roomId() == c.roomId()) return "Room clash with " + where + " in " + o.roomName() + ".";
                if (o.facultyId() == c.facultyId()) return "Faculty clash: " + o.facultyName() + " already teaches " + where + ".";
                if (o.className().equalsIgnoreCase(c.className())) return "Class clash: " + c.className() + " already has " + where + ".";
            }
            for (Booking b : approved) {
                if (b.roomId() == c.roomId() && Dt.overlaps(s, e, b.start(), b.end())) {
                    return "An approved booking (" + b.purpose() + ") already holds this room on " + d.format(Dt.DATE) + " " + Dt.range(b.start(), b.end()) + ".";
                }
            }
        }
        return null;
    }

    /** Scans the next days for overlapping rooms, faculty, classes and approved bookings. */
    public List<String> detectConflicts(LocalDate from, int days) {
        Set<String> out = new LinkedHashSet<>();
        List<TimetableEntry> all = dao.listAll();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            List<ClassSlot> slots = expand(all, d, d);
            for (int a = 0; a < slots.size(); a++) {
                for (int b = a + 1; b < slots.size(); b++) {
                    ClassSlot x = slots.get(a), y = slots.get(b);
                    if (!Dt.overlaps(x.start(), x.end(), y.start(), y.end())) continue;
                    String when = d.format(Dt.DATE) + " " + Dt.range(x.start(), x.end());
                    if (x.entry().roomId() == y.entry().roomId()) out.add("Room clash in " + x.entry().roomName() + " · " + when);
                    if (x.entry().facultyId() == y.entry().facultyId()) out.add("Faculty clash for " + x.entry().facultyName() + " · " + when);
                    if (x.entry().className().equalsIgnoreCase(y.entry().className())) out.add("Class clash for " + x.entry().className() + " · " + when);
                }
            }
            for (Booking bk : bookings.blocking(d.atStartOfDay(), d.plusDays(1).atStartOfDay())) {
                for (ClassSlot s : slots) {
                    if (s.entry().roomId() == bk.roomId() && Dt.overlaps(s.start(), s.end(), bk.start(), bk.end())) {
                        out.add("Booking overlaps class in " + bk.roomName() + " · " + d.format(Dt.DATE) + " " + Dt.range(bk.start(), bk.end()));
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }
}
