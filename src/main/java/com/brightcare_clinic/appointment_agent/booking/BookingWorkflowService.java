package com.brightcare_clinic.appointment_agent.booking;

import org.springframework.stereotype.Service;

@Service
public class BookingWorkflowService {

    public BookingResponse checkAvailability(BookingRequest request) {
        return BookingResponse.builder()
                .status(BookingStatus.SLOT_UNAVAILABLE)
                .message("Tomorrow at 2 PM is unavailable.")
                .suggestedSlot("Tomorrow at 3 PM")
                .build();
    }

}
