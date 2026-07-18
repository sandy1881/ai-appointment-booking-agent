package com.brightcare_clinic.appointment_agent.booking;

import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class BookingWorkflowService {

    private final GoogleCalendarService googleCalendarService;

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

    public BookingStatus confirmAppointment(BookingRequest request) throws IOException {
        return googleCalendarService.createAppointment(request);
    }

}
