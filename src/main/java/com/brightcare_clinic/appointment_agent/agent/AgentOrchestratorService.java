package com.brightcare_clinic.appointment_agent.agent;

import com.brightcare_clinic.appointment_agent.ai.IntentService;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingResponse;
import com.brightcare_clinic.appointment_agent.booking.BookingWorkflowService;
import com.brightcare_clinic.appointment_agent.conversation.ConversationState;
import com.brightcare_clinic.appointment_agent.conversation.SessionService;
import com.brightcare_clinic.appointment_agent.conversation.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final IntentService intentService;
    private final SessionService sessionService;
    private final BookingWorkflowService bookingWorkflowService;

    public String processMessage(Long chatId, String message) {
        UserSession session = sessionService.getSession(chatId);

        String response = switch (session.getState()) {
            case WAITING_FOR_SLOT_CONFIRMATION -> handleSlotConfirmation(session, message);
            case WAITING_FOR_EMAIL -> handleEmailCollection(session, message);
            default -> handleByIntent(session, message);
        };

        sessionService.saveSession(session);
        return response;
    }

    private String handleByIntent(UserSession session, String message) {
        IntentResult intentResult = intentService.detectIntent(message);

        return switch (intentResult.getIntentType()) {
            case GREETING -> "Hello! Welcome to BrightCare Clinic. How can I help you today?";
            case BOOK_APPOINTMENT -> {
                BookingResponse bookingResponse = bookingWorkflowService.checkAvailability(new BookingRequest());
                session.setState(ConversationState.WAITING_FOR_SLOT_CONFIRMATION);
                yield bookingResponse.getMessage() + " Would you like " + bookingResponse.getSuggestedSlot() + " instead?";
            }
            case FAQ -> "Let me help answer your question.";
            default -> "I'm not sure I understood that. Could you rephrase?";
        };
    }

    private String handleSlotConfirmation(UserSession session, String message) {
        if (message.trim().equalsIgnoreCase("yes")) {
            session.setState(ConversationState.WAITING_FOR_EMAIL);
            return "Great! What's your email address?";
        }
        session.setState(ConversationState.GREETING);
        return "No problem, let's start over. How can I help you today?";
    }

    private String handleEmailCollection(UserSession session, String message) {
        session.setState(ConversationState.BOOKING_COMPLETED);
        return "Thanks! Your appointment request has been recorded.";
    }

}
