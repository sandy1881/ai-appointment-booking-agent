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

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingWorkflowService {

    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;

    public BookingResponse checkAvailability(BookingRequest request) throws IOException {
        CalendarSlot requestedSlot = googleCalendarService.checkAvailability(request.getAppointmentDate(), request.getAppointmentTime());

        if (requestedSlot.isAvailable()) {
            return BookingResponse.builder()
                    .status(BookingStatus.SLOT_AVAILABLE)
                    .message(requestedSlot.getMessage())
                    .build();
        }

        CalendarSlot nextSlot = googleCalendarService.findNextAvailableSlot(request.getAppointmentDate(), request.getAppointmentTime());

        return BookingResponse.builder()
                .status(BookingStatus.SLOT_UNAVAILABLE)
                .message(requestedSlot.getMessage())
                .suggestedSlot(nextSlot)
                .build();
    }

    public BookingResponse confirmAppointment(BookingRequest request) throws IOException {
        BookingStatus status = googleCalendarService.createAppointment(request);
        String message = "Your appointment is confirmed.";

        if (request.getEmail() != null) {
            try {
                emailService.sendAppointmentConfirmation(new EmailRequest(
                        request.getEmail(), request.getPatientName(), request.getAppointmentDate(), request.getAppointmentTime()));
            } catch (EmailException e) {
                log.error("Failed to send confirmation email to {}", request.getEmail(), e);
                message = "Your appointment is confirmed. We couldn't send the confirmation email.";
            }
        }

        log.info("Booking confirmed for {} on {} at {}", request.getPatientName(), request.getAppointmentDate(), request.getAppointmentTime());

        return BookingResponse.builder()
                .status(status)
                .message(message)
                .build();
    }

}
