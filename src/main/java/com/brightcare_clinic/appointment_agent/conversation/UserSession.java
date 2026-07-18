package com.brightcare_clinic.appointment_agent.conversation;

import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UserSession {

    // Keeps the last few exchanges only, so the Gemini prompt stays small and cheap
    // rather than growing unbounded over a long conversation.
    private static final int MAX_HISTORY_ENTRIES = 8;

    private final Long chatId;
    private ConversationState state;
    private BookingRequest pendingBooking;
    private List<String> conversationHistory = new ArrayList<>();

    @JsonCreator
    public UserSession(@JsonProperty("chatId") Long chatId) {
        this.chatId = chatId;
        this.state = ConversationState.GREETING;
    }

    public void recordTurn(String userMessage, String botReply) {
        conversationHistory.add("User: " + userMessage);
        conversationHistory.add("Bot: " + botReply);
        while (conversationHistory.size() > MAX_HISTORY_ENTRIES) {
            conversationHistory.remove(0);
        }
    }

}
