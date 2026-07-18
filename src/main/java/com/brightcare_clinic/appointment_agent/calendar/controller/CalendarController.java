package com.brightcare_clinic.appointment_agent.calendar.controller;

import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.calendar.model.AppointmentResponse;
import com.brightcare_clinic.appointment_agent.calendar.model.AvailabilityResponse;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarEventResponse;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.calendar.model.SlotResponse;
import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import com.google.api.services.calendar.model.EventDateTime;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final GoogleCalendarService calendarService;

    @GetMapping("/test")
    public String test() throws IOException {
        calendarService.getCalendarClient().calendarList().list().execute();
        return "Google Calendar authenticated successfully";
    }

    @GetMapping("/events")
    public List<CalendarEventResponse> events() throws IOException {
        return calendarService.getTodayEvents().stream()
                .map(event -> new CalendarEventResponse(
                        event.getSummary(),
                        formatEventTime(event.getStart()),
                        formatEventTime(event.getEnd())
                ))
                .toList();
    }

    @GetMapping("/check-availability")
    public AvailabilityResponse checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) throws IOException {
        CalendarSlot slot = calendarService.checkAvailability(date, time);
        return new AvailabilityResponse(slot.isAvailable());
    }

    @GetMapping("/next-slot")
    public SlotResponse nextSlot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) throws IOException {
        CalendarSlot slot = calendarService.findNextAvailableSlot(date, time);
        return new SlotResponse(slot.getDate(), slot.getStartTime(), slot.getEndTime());
    }

    @PostMapping("/create-appointment")
    public AppointmentResponse createAppointment(@Valid @RequestBody BookingRequest request) throws IOException {
        BookingStatus status = calendarService.createAppointment(request);
        return new AppointmentResponse(status);
    }

    private String formatEventTime(EventDateTime eventDateTime) {
        long millis = eventDateTime.getDateTime() != null
                ? eventDateTime.getDateTime().getValue()
                : eventDateTime.getDate().getValue();
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
    }

}
