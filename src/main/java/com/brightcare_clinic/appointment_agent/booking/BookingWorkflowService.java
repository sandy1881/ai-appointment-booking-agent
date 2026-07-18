package com.brightcare_clinic.appointment_agent.booking;

import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import com.brightcare_clinic.appointment_agent.email.dto.EmailRequest;
import com.brightcare_clinic.appointment_agent.email.exception.EmailException;
import com.brightcare_clinic.appointment_agent.email.service.EmailService;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingWorkflowService {

    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;

    public BookingResponse checkAvailability(BookingRequest request) throws IOException {
        LocalDate date = request.getAppointmentDate();
        LocalTime time = request.getAppointmentTime();

        if (!googleCalendarService.isWithinBusinessHours(date, time)) {
            return BookingResponse.builder()
                    .status(BookingStatus.SLOT_UNAVAILABLE)
                    .message("That time is outside our business hours (Monday to Friday, 9:00 AM to 6:00 PM). "
                            + "What other date and time would you like?")
                    .build();
        }

        CalendarSlot requestedSlot = googleCalendarService.checkAvailability(date, time);

        if (requestedSlot.isAvailable()) {
            return BookingResponse.builder()
                    .status(BookingStatus.SLOT_AVAILABLE)
                    .message(BookingTimeFormatter.formatDateTime(date, time) + " is available.")
                    .build();
        }

        CalendarSlot nextSlot = googleCalendarService.findNextAvailableSlot(date, time);

        if (!nextSlot.isAvailable()) {
            return BookingResponse.builder()
                    .status(BookingStatus.SLOT_UNAVAILABLE)
                    .message(BookingTimeFormatter.formatTime(time) + " " + BookingTimeFormatter.formatDayName(date)
                            + " isn't available, and there are no more openings later that day. What other day works for you?")
                    .build();
        }

        return BookingResponse.builder()
                .status(BookingStatus.SLOT_UNAVAILABLE)
                .message(BookingTimeFormatter.formatTime(time) + " " + BookingTimeFormatter.formatDayName(date)
                        + " isn't available. The nearest opening is " + BookingTimeFormatter.formatTime(nextSlot.getStartTime()) + ".")
                .suggestedSlot(nextSlot)
                .build();
    }

    public BookingResponse confirmAppointment(BookingRequest request) throws IOException {
        BookingStatus status = googleCalendarService.createAppointment(request);
        String slotDescription = BookingTimeFormatter.formatDateTime(request.getAppointmentDate(), request.getAppointmentTime());

        if (status == BookingStatus.SLOT_TAKEN) {
            log.warn("Booking conflict for {} on {} at {}", request.getPatientName(), request.getAppointmentDate(), request.getAppointmentTime());
            return BookingResponse.builder()
                    .status(BookingStatus.SLOT_TAKEN)
                    .message("Sorry, " + slotDescription + " was just booked by someone else. What other date and time would you like?")
                    .build();
        }

        String message = "Done — you're booked for " + slotDescription + ". Anything else?";

        if (request.getEmail() != null) {
            try {
                emailService.sendAppointmentConfirmation(new EmailRequest(
                        request.getEmail(), request.getPatientName(), request.getAppointmentDate(), request.getAppointmentTime()));
            } catch (EmailException e) {
                log.error("Failed to send confirmation email to {}", request.getEmail(), e);
                message = "Done — you're booked for " + slotDescription + ". We couldn't send the confirmation email, though.";
            }
        }

        log.info("Booking confirmed for {} on {} at {}", request.getPatientName(), request.getAppointmentDate(), request.getAppointmentTime());

        return BookingResponse.builder()
                .status(status)
                .message(message)
                .build();
    }

    public BookingResponse findAppointmentToCancel(LocalDate date, LocalTime time) throws IOException {
        Optional<Event> event = googleCalendarService.findEvent(date, time);
        String slotDescription = BookingTimeFormatter.formatDateTime(date, time);

        if (event.isEmpty()) {
            return BookingResponse.builder()
                    .status(BookingStatus.NOT_FOUND)
                    .message("I couldn't find an appointment for " + slotDescription + ". Could you double-check the date and time?")
                    .build();
        }

        return BookingResponse.builder()
                .status(BookingStatus.PENDING)
                .message("Found your appointment for " + slotDescription + ". Shall I cancel it?")
                .build();
    }

    public BookingResponse cancelAppointment(LocalDate date, LocalTime time) throws IOException {
        Optional<Event> event = googleCalendarService.findEvent(date, time);
        String slotDescription = BookingTimeFormatter.formatDateTime(date, time);

        if (event.isEmpty()) {
            return BookingResponse.builder()
                    .status(BookingStatus.NOT_FOUND)
                    .message("That appointment doesn't seem to be there anymore.")
                    .build();
        }

        String attendeeEmail = attendeeEmail(event.get());
        String patientName = patientNameFromSummary(event.get().getSummary());

        BookingStatus status = googleCalendarService.cancelAppointment(date, time);
        String message = "Your appointment for " + slotDescription + " has been cancelled.";

        if (attendeeEmail != null) {
            try {
                emailService.sendCancellation(new EmailRequest(attendeeEmail, patientName, date, time));
            } catch (EmailException e) {
                log.error("Failed to send cancellation email to {}", attendeeEmail, e);
                message += " We couldn't send a cancellation email, though.";
            }
        }

        log.info("Cancelled appointment for {} on {} at {}", patientName, date, time);

        return BookingResponse.builder()
                .status(status)
                .message(message)
                .build();
    }

    private String attendeeEmail(Event event) {
        List<EventAttendee> attendees = event.getAttendees();
        return attendees != null && !attendees.isEmpty() ? attendees.get(0).getEmail() : null;
    }

    private String patientNameFromSummary(String summary) {
        String prefix = "Appointment - ";
        return summary != null && summary.startsWith(prefix) ? summary.substring(prefix.length()) : summary;
    }

}
