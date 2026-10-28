package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.BookingDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.dao.UserDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.Role;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.User;
import com.sakec.smartspace.security.Permissions;
import com.sakec.smartspace.security.Session;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Db;
import com.sakec.smartspace.util.Dt;
import com.sakec.smartspace.util.Log;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Booking workflow. Every conflict check and write happens inside one database transaction guarded by a
 * lock, so two requests cannot both pass the check for the same slot.
 */
public class BookingService {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private final BookingDao bookings = new BookingDao();
    private final RoomDao rooms = new RoomDao();
    private final UserDao users = new UserDao();
    private final AvailabilityService availability = new AvailabilityService();
    private final NotificationService notifications = new NotificationService();
    private final EmailService mailer = new EmailService();

    public void validateSlot(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null) throw new AppException("Choose a date, start time and end time.");
        if (!end.isAfter(start)) throw new AppException("The end time must be after the start time.");
        LocalDateTime s = date.atTime(start), e = date.atTime(end);
        if (Duration.between(s, e).toMinutes() < 15) throw new AppException("Bookings must be at least 15 minutes long.");
        if (Duration.between(s, e).toHours() > 6) throw new AppException("Bookings can be at most 6 hours long.");
        if (!s.isAfter(LocalDateTime.now())) throw new AppException("Choose a start time in the future.");
        if (date.isAfter(LocalDate.now().plusDays(60))) throw new AppException("You can book at most 60 days ahead.");
    }

    /** Live check used by the booking form. Returns the availability for the chosen slot. */
    public RoomAvailability checkSlot(int roomId, LocalDate date, LocalTime start, LocalTime end) {
        validateSlot(date, start, end);
        return availability.check(roomId, date.atTime(start), date.atTime(end));
    }

    public Booking request(int roomId, LocalDate date, LocalTime start, LocalTime end, String purpose) {
        User me = Permissions.require(Role.STUDENT, Role.TEACHER);
        validateSlot(date, start, end);
        if (purpose == null || purpose.isBlank()) throw new AppException("Tell us what the room is needed for.");
        if (purpose.trim().length() > 200) throw new AppException("Keep the purpose under 200 characters.");
        Room room = rooms.findById(roomId);
        if (room == null || !room.active()) throw new AppException("That room can't be booked.");
        LocalDateTime s = date.atTime(start), e = date.atTime(end);

        LOCK.lock();
        try {
            long id = Db.inTransaction(c -> {
                RoomAvailability a = availability.check(c, room, s, e);
                if (a.status() != RoomStatus.AVAILABLE) {
                    throw new AppException("That slot isn't free (" + a.status().label() + ": " + a.detail() + ").");
                }
                return bookings.insert(c, roomId, me.id(), purpose.trim(), s, e);
            });
            Booking requested = bookings.findById((int) id);
            notifications.notifyCoordinators("New room request",
                    me.fullName() + " requested " + room.name() + " on " + date.format(Dt.DATE) + ", "
                            + start.format(Dt.TIME) + "–" + end.format(Dt.TIME) + ".");
            mailer.send(me.email(), "SmartSpace room request confirmation",
                    "Hello " + me.firstName() + ",\n\nYour room booking request for " + room.name() + " on "
                            + date.format(Dt.DATE) + " from " + start.format(Dt.TIME) + " to " + end.format(Dt.TIME)
                            + " has been submitted and is awaiting coordinator approval.\n\nPurpose: " + purpose.trim()
                            + "\n\nYou will receive another email when the request is approved or rejected."
            );
            Log.info("Booking requested id=" + id + " by userId=" + me.id());
            return requested;
        } finally {
            LOCK.unlock();
        }
    }

    public void approve(int bookingId, String note) { review(bookingId, BookingStatus.APPROVED, note); }

    public void reject(int bookingId, String note) { review(bookingId, BookingStatus.REJECTED, note); }

    private void review(int bookingId, BookingStatus decision, String note) {
        User me = Permissions.require(Role.COORDINATOR);
        LOCK.lock();
        try {
            Booking b = Db.inTransaction(c -> {
                Booking current = bookings.findById(c, bookingId);
                if (current == null) throw new AppException("That request no longer exists.");
                if (current.status() != BookingStatus.PENDING) throw new AppException("This request was already handled.");
                if (decision == BookingStatus.APPROVED) {
                    if (!current.end().isAfter(LocalDateTime.now())) throw new AppException("This slot is already in the past.");
                    Room room = rooms.findById(current.roomId());
                    RoomAvailability a = availability.check(c, room, current.start(), current.end());
                    if (a.status() != RoomStatus.AVAILABLE) {
                        throw new AppException("Can't approve: " + a.status().label() + " (" + a.detail() + ").");
                    }
                }
                bookings.review(c, bookingId, decision, me.id(), note == null || note.isBlank() ? null : note.trim());
                return current;
            });
            String verb = decision == BookingStatus.APPROVED ? "approved" : "rejected";
            String extra = note == null || note.isBlank() ? "" : " Note: " + note.trim();
            notifications.notifyUser(b.userId(), "Booking " + verb, "Your request for " + b.roomName() + " on "
                    + b.start().format(Dt.DATE) + ", " + Dt.range(b.start(), b.end()) + " was " + verb + "." + extra);
            User requester = users.findById(b.userId());
            if (requester != null) {
                String outcomeText = decision == BookingStatus.APPROVED
                        ? "approved"
                        : "rejected";
                mailer.send(requester.email(), "SmartSpace booking " + outcomeText,
                        "Hello " + requester.firstName() + ",\n\nYour room request for " + b.roomName() + " on "
                                + b.start().format(Dt.DATE) + " from " + b.start().format(Dt.TIME) + " to "
                                + b.end().format(Dt.TIME) + " was " + outcomeText + "." + extra
                                + "\n\nIf you need help, please contact the timetable coordinator."
                );
            }
        } finally {
            LOCK.unlock();
        }
    }

    public void cancel(int bookingId) {
        User me = Session.user();
        Booking b = bookings.findById(bookingId);
        if (b == null) throw new AppException("That booking no longer exists.");
        if (b.userId() != me.id() && me.role() != Role.COORDINATOR) throw new AppException("You can only cancel your own bookings.");
        if (b.status() != BookingStatus.PENDING && b.status() != BookingStatus.APPROVED) {
            throw new AppException("This booking can't be cancelled any more.");
        }
        Db.inTransaction(c -> { bookings.setStatus(c, bookingId, BookingStatus.CANCELLED); return null; });
        if (b.userId() != me.id()) {
            notifications.notifyUser(b.userId(), "Booking cancelled", "Your booking for " + b.roomName() + " on "
                    + b.start().format(Dt.DATE) + " was cancelled by the coordinator.");
        } else if (b.status() == BookingStatus.APPROVED) {
            notifications.notifyCoordinators("Booking cancelled", me.fullName() + " cancelled " + b.roomName() + " on "
                    + b.start().format(Dt.DATE) + ".");
        }
    }

    public List<Booking> mine() { return bookings.listByUser(Session.user().id()); }

    public List<Booking> pending() {
        Permissions.require(Role.COORDINATOR);
        return bookings.listByStatus(BookingStatus.PENDING);
    }

    public int latestRequestId() {
        Permissions.require(Role.COORDINATOR);
        return bookings.latestBookingId();
    }

    public List<Integer> requestIdsAfter(int id) {
        Permissions.require(Role.COORDINATOR);
        return bookings.bookingIdsAfter(id);
    }

    public List<Booking> all() {
        Permissions.require(Role.COORDINATOR);
        return bookings.listAll();
    }

    /** Approved bookings overlapping a window (read-only, visible to everyone for the timetable view). */
    public List<Booking> approvedBetween(LocalDateTime from, LocalDateTime to) { return bookings.blocking(from, to); }

    /** Marks approved bookings whose time has passed as Completed. */
    public void completePast() { bookings.completePast(LocalDateTime.now()); }
}
