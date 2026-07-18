package com.brightcare_clinic.appointment_agent.calendar.service;

import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarConstants;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final ObjectProvider<Calendar> calendarClientProvider;

    public Calendar getCalendarClient() {
        return calendarClientProvider.getObject();
    }

    public List<Event> getTodayEvents() throws IOException {
        LocalDate today = LocalDate.now();
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
        boolean available = !hasConflict(date, time);
        String message = available ? "Requested slot is available." : "Requested slot is unavailable.";
        return new CalendarSlot(date, time, time.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES), available, message);
    }

    public CalendarSlot findNextAvailableSlot(LocalDate date, LocalTime time) throws IOException {
        LocalDate searchDate = date;
        LocalTime searchTime = time.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES);

        for (int attempt = 0; attempt < CalendarConstants.MAX_SLOT_SEARCH_ATTEMPTS; attempt++) {
            if (searchTime.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES).isAfter(CalendarConstants.BUSINESS_END_TIME)) {
                searchDate = searchDate.plusDays(1);
                searchTime = CalendarConstants.BUSINESS_START_TIME;
            }

            if (!hasConflict(searchDate, searchTime)) {
                return new CalendarSlot(
                        searchDate,
                        searchTime,
                        searchTime.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES),
                        true,
                        "Next available slot found.");
            }

            searchTime = searchTime.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES);
        }

        return new CalendarSlot(date, time, time.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES), false,
                "No available slot found in the searched window.");
    }

    public BookingStatus createAppointment(BookingRequest request) throws IOException {
        LocalDateTime start = request.getAppointmentDate().atTime(request.getAppointmentTime());
        LocalDateTime end = start.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES);
        String zoneId = ZoneId.systemDefault().getId();

        Event event = new Event()
                .setSummary("Appointment - " + request.getPatientName())
                .setStart(new EventDateTime().setDateTime(toDateTime(start)).setTimeZone(zoneId))
                .setEnd(new EventDateTime().setDateTime(toDateTime(end)).setTimeZone(zoneId));

        if (request.getEmail() != null) {
            event.setAttendees(List.of(new EventAttendee().setEmail(request.getEmail())));
        }

        getCalendarClient().events().insert(CalendarConstants.PRIMARY_CALENDAR_ID, event).execute();
        return BookingStatus.CONFIRMED;
    }

    private boolean hasConflict(LocalDate date, LocalTime time) throws IOException {
        DateTime timeMin = toDateTime(date, time);
        DateTime timeMax = toDateTime(date, time.plusMinutes(CalendarConstants.SLOT_DURATION_MINUTES));

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
        return new DateTime(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

}
