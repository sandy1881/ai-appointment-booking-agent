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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final ObjectProvider<Calendar> calendarClientProvider;
    private final BookingProperties bookingProperties;
    private final ClinicProperties clinicProperties;

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

    public CalendarSlot checkAvailability(LocalDate date, LocalTime time) throws IOException {
        log.info("Checking calendar availability for {} at {}", date, time);
        boolean available = !hasConflict(date, time);
        String message = available ? "Requested slot is available." : "Requested slot is unavailable.";
        return new CalendarSlot(date, time, time.plusMinutes(bookingProperties.getSlotDuration()), available, message);
    }

    public CalendarSlot findNextAvailableSlot(LocalDate date, LocalTime time) throws IOException {
        LocalDate searchDate = date;
        LocalTime searchTime = time.plusMinutes(bookingProperties.getSlotDuration());

        for (int attempt = 0; attempt < CalendarConstants.MAX_SLOT_SEARCH_ATTEMPTS; attempt++) {
            if (searchTime.plusMinutes(bookingProperties.getSlotDuration()).isAfter(bookingProperties.getBusinessEnd())) {
                searchDate = searchDate.plusDays(1);
                searchTime = bookingProperties.getBusinessStart();
            }

            if (!hasConflict(searchDate, searchTime)) {
                return new CalendarSlot(
                        searchDate,
                        searchTime,
                        searchTime.plusMinutes(bookingProperties.getSlotDuration()),
                        true,
                        "Next available slot found.");
            }

            searchTime = searchTime.plusMinutes(bookingProperties.getSlotDuration());
        }

        return new CalendarSlot(date, time, time.plusMinutes(bookingProperties.getSlotDuration()), false,
                "No available slot found in the searched window.");
    }

    public BookingStatus createAppointment(BookingRequest request) throws IOException {
        log.info("Creating calendar event for {} on {} at {}", request.getPatientName(), request.getAppointmentDate(), request.getAppointmentTime());

        LocalDateTime start = request.getAppointmentDate().atTime(request.getAppointmentTime());
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
    }

    private boolean hasConflict(LocalDate date, LocalTime time) throws IOException {
        DateTime timeMin = toDateTime(date, time);
        DateTime timeMax = toDateTime(date, time.plusMinutes(bookingProperties.getSlotDuration()));

        List<Event> events = getCalendarClient().events().list(CalendarConstants.PRIMARY_CALENDAR_ID)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .execute()
                .getItems();

        return !events.isEmpty();
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
