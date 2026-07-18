package com.brightcare_clinic.appointment_agent.calendar.service;

import com.brightcare_clinic.appointment_agent.booking.BookingProperties;
import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarConstants;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final ObjectProvider<Calendar> calendarClientProvider;
    private final BookingProperties bookingProperties;
    private final ClinicProperties clinicProperties;

    // Serializes check-then-write for appointment creation within this JVM instance, so two
    // near-simultaneous bookings for the same slot can't both pass the conflict check before
    // either one writes. A single coarse-grained lock is enough at this app's scale (one small
    // clinic, infrequent bookings) - a per-slot lock or distributed lock would be over-engineering
    // for a single-instance deployment with no horizontal scaling.
    private final ReentrantLock bookingLock = new ReentrantLock();

    public Calendar getCalendarClient() {
        return calendarClientProvider.getObject();
    }

    public List<Event> getTodayEvents() throws IOException {
        LocalDate today = LocalDate.now(zoneId());
        DateTime timeMin = toDateTime(today, LocalTime.MIDNIGHT);
        DateTime timeMax = toDateTime(today.plusDays(1), LocalTime.MIDNIGHT);

        return getCalendarClient().events().list(CalendarConstants.PRIMARY_CALENDAR_ID)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()
                .getItems();
    }

    public boolean isWithinBusinessHours(LocalDate date, LocalTime time) {
        boolean isWeekday = date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
        boolean fitsWithinHours = !time.isBefore(bookingProperties.getBusinessStart())
                && !time.plusMinutes(bookingProperties.getSlotDuration()).isAfter(bookingProperties.getBusinessEnd());
        return isWeekday && fitsWithinHours;
    }

    public CalendarSlot checkAvailability(LocalDate date, LocalTime time) throws IOException {
        log.info("Checking calendar availability for {} at {}", date, time);
        boolean available = !hasConflict(date, time);
        String message = available ? "Requested slot is available." : "Requested slot is unavailable.";
        return new CalendarSlot(date, time, time.plusMinutes(bookingProperties.getSlotDuration()), available, message);
    }

    /**
     * Searches for the next open slot on the same day only, at or after the requested time.
     * Never rolls over to a later day - callers should tell the user if none remain today.
     */
    public CalendarSlot findNextAvailableSlot(LocalDate date, LocalTime time) throws IOException {
        LocalTime searchTime = time.plusMinutes(bookingProperties.getSlotDuration());

        while (!searchTime.plusMinutes(bookingProperties.getSlotDuration()).isAfter(bookingProperties.getBusinessEnd())) {
            if (!hasConflict(date, searchTime)) {
                return new CalendarSlot(date, searchTime, searchTime.plusMinutes(bookingProperties.getSlotDuration()), true,
                        "Next available slot found.");
            }
            searchTime = searchTime.plusMinutes(bookingProperties.getSlotDuration());
        }

        return new CalendarSlot(date, time, time.plusMinutes(bookingProperties.getSlotDuration()), false,
                "No available slot found later today.");
    }

    public BookingStatus createAppointment(BookingRequest request) throws IOException {
        LocalDate date = request.getAppointmentDate();
        LocalTime time = request.getAppointmentTime();

        bookingLock.lock();
        try {
            // Re-check right before writing: the original availability check may have happened
            // several conversation turns ago, so another booking (or a manual calendar edit)
            // could have taken this slot in the meantime.
            if (hasConflict(date, time)) {
                log.warn("Slot no longer available for {} on {} at {} - booked concurrently", request.getPatientName(), date, time);
                return BookingStatus.SLOT_TAKEN;
            }

            log.info("Creating calendar event for {} on {} at {}", request.getPatientName(), date, time);

            LocalDateTime start = date.atTime(time);
            LocalDateTime end = start.plusMinutes(bookingProperties.getSlotDuration());
            String zoneId = zoneId().getId();

            Event event = new Event()
                    .setSummary("Appointment - " + request.getPatientName())
                    .setStart(new EventDateTime().setDateTime(toDateTime(start)).setTimeZone(zoneId))
                    .setEnd(new EventDateTime().setDateTime(toDateTime(end)).setTimeZone(zoneId));

            if (request.getEmail() != null) {
                event.setAttendees(List.of(new EventAttendee().setEmail(request.getEmail())));
            }

            getCalendarClient().events().insert(CalendarConstants.PRIMARY_CALENDAR_ID, event).execute();
            log.info("Calendar event created for {}", request.getPatientName());
            return BookingStatus.CONFIRMED;
        } finally {
            bookingLock.unlock();
        }
    }

    public Optional<Event> findEvent(LocalDate date, LocalTime time) throws IOException {
        return listEventsAt(date, time).stream().findFirst();
    }

    public BookingStatus cancelAppointment(LocalDate date, LocalTime time) throws IOException {
        Optional<Event> event = findEvent(date, time);
        if (event.isEmpty()) {
            return BookingStatus.NOT_FOUND;
        }

        getCalendarClient().events().delete(CalendarConstants.PRIMARY_CALENDAR_ID, event.get().getId()).execute();
        log.info("Cancelled calendar event {} on {} at {}", event.get().getId(), date, time);
        return BookingStatus.CANCELLED;
    }

    private boolean hasConflict(LocalDate date, LocalTime time) throws IOException {
        return !listEventsAt(date, time).isEmpty();
    }

    private List<Event> listEventsAt(LocalDate date, LocalTime time) throws IOException {
        DateTime timeMin = toDateTime(date, time);
        DateTime timeMax = toDateTime(date, time.plusMinutes(bookingProperties.getSlotDuration()));

        return getCalendarClient().events().list(CalendarConstants.PRIMARY_CALENDAR_ID)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .execute()
                .getItems();
    }

    private DateTime toDateTime(LocalDate date, LocalTime time) {
        return toDateTime(date.atTime(time));
    }

    private DateTime toDateTime(LocalDateTime dateTime) {
        return new DateTime(dateTime.atZone(zoneId()).toInstant().toEpochMilli());
    }

    private ZoneId zoneId() {
        return ZoneId.of(clinicProperties.getTimezone());
    }

}
