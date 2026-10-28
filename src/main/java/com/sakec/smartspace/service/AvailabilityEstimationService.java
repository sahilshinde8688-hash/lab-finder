package com.sakec.smartspace.service;

import com.sakec.smartspace.dao.BookingDao;
import com.sakec.smartspace.dao.RoomDao;
import com.sakec.smartspace.model.Booking;
import com.sakec.smartspace.model.BookingStatus;
import com.sakec.smartspace.model.Room;
import com.sakec.smartspace.model.RoomAvailability;
import com.sakec.smartspace.model.RoomStatus;
import com.sakec.smartspace.model.RoomType;
import com.sakec.smartspace.model.TimetableEntry;
import com.sakec.smartspace.util.AppException;
import com.sakec.smartspace.util.Dt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Rule-based estimates from the last eight comparable weekdays; authoritative conflicts always take precedence. */
public class AvailabilityEstimationService {
    public record Recommendation(RoomAvailability availability, double estimatedFreeLikelihood,
                                 int comparableSlots, int historicallyUsedSlots) {
        public String explanation() {
            String currentSlot = "No timetable, approved-booking, or maintenance conflict is recorded for the requested slot. ";
            if (comparableSlots == 0) {
                return currentSlot
                        + "No comparable historical slots are recorded; this is a low-confidence estimate.";
            }
            return currentSlot + "Schedule records show use in " + historicallyUsedSlots + " of " + comparableSlots
                    + " comparable weekday/time slots. This is a historical estimate, not a booking guarantee.";
        }
    }

    private static final int LOOKBACK_WEEKS = 8;
    private final RoomDao rooms;
    private final BookingDao bookings;
    private final AvailabilityService availability;

    public AvailabilityEstimationService() {
        this(new RoomDao(), new BookingDao(), new AvailabilityService());
    }

    AvailabilityEstimationService(RoomDao rooms, BookingDao bookings, AvailabilityService availability) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.availability = availability;
    }

    public List<Recommendation> estimate(LocalDateTime from, LocalDateTime to, RoomType type,
                                         Integer minCapacity, String equipment) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new AppException("Choose a valid future time slot.");
        }
        if (!from.isAfter(LocalDateTime.now())) {
            throw new AppException("Predictions are available only for future time slots.");
        }
        if (minCapacity != null && minCapacity < 0) {
            throw new AppException("Minimum capacity cannot be negative.");
        }

        List<Room> candidates = rooms.search(null, type, minCapacity, equipment, false);
        AvailabilityService.Snapshot current = availability.snapshot(null, from, to);
        LocalDateTime historyFrom = LocalDate.now().minusWeeks(LOOKBACK_WEEKS).atStartOfDay();
        List<Booking> history = bookings.blocking(historyFrom, LocalDateTime.now());
        return rank(candidates, from, to, current, history);
    }

    static List<Recommendation> rank(List<Room> candidates, LocalDateTime from, LocalDateTime to,
                                     AvailabilityService.Snapshot current, List<Booking> history) {
        List<Recommendation> results = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate firstObservation = today.minusWeeks(LOOKBACK_WEEKS);
        List<LocalDate> comparableDays = new ArrayList<>();
        for (LocalDate day = today.minusDays(1); !day.isBefore(firstObservation); day = day.minusDays(1)) {
            if (day.getDayOfWeek() == from.getDayOfWeek() && comparableDays.size() < LOOKBACK_WEEKS) {
                comparableDays.add(day);
            }
        }

        for (Room room : candidates) {
            RoomAvailability roomAvailability = AvailabilityService.compute(room, from, to, current);
            if (roomAvailability.status() != RoomStatus.AVAILABLE) continue;

            int used = 0;
            for (LocalDate day : comparableDays) {
                LocalDateTime slotStart = day.atTime(from.toLocalTime());
                long dayOffset = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
                LocalDateTime slotEnd = day.plusDays(dayOffset).atTime(to.toLocalTime());
                boolean timetableUse = current.timetable().stream()
                        .filter(entry -> entry.roomId() == room.id() && entry.occursOn(day))
                        .anyMatch(entry -> Dt.overlaps(slotStart, slotEnd,
                                entry.startOn(day), entry.endOn(day)));
                boolean bookingUse = history.stream()
                        .filter(booking -> booking.roomId() == room.id()
                                && (booking.status() == BookingStatus.APPROVED
                                || booking.status() == BookingStatus.COMPLETED))
                        .anyMatch(booking -> Dt.overlaps(slotStart, slotEnd, booking.start(), booking.end()));
                if (timetableUse || bookingUse) used++;
            }
            int observations = comparableDays.size();
            double likelihood = 1.0 - (used + 1.0) / (observations + 2.0);
            results.add(new Recommendation(roomAvailability, likelihood, observations, used));
        }
        results.sort(Comparator.comparingDouble(Recommendation::estimatedFreeLikelihood).reversed()
                .thenComparing(recommendation -> recommendation.availability().room().floor())
                .thenComparing(recommendation -> recommendation.availability().room().name()));
        return List.copyOf(results);
    }
}
