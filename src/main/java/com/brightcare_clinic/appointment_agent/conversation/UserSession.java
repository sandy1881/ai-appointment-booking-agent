package com.brightcare_clinic.appointment_agent.conversation;

import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSession {

    private final Long chatId;
    private ConversationState state;
    private BookingRequest pendingBooking;

    public UserSession(Long chatId) {
        this.chatId = chatId;
        this.state = ConversationState.GREETING;
    }

}
