package com.brightcare_clinic.appointment_agent.booking;

import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import com.brightcare_clinic.appointment_agent.email.dto.EmailRequest;
import com.brightcare_clinic.appointment_agent.email.exception.EmailException;
import com.brightcare_clinic.appointment_agent.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

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

}
