package com.brightcare_clinic.appointment_agent.booking;

import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {

    private final BookingStatus status;
    private final String message;
    private final CalendarSlot suggestedSlot;

}
