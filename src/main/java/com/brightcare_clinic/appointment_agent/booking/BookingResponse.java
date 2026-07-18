package com.brightcare_clinic.appointment_agent.booking;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {

    private BookingStatus status;
    private String message;
    private String suggestedSlot;

}
