package com.brightcare_clinic.appointment_agent.agent;

import com.brightcare_clinic.appointment_agent.ai.IntentService;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingResponse;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.booking.BookingWorkflowService;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.conversation.ConversationState;
import com.brightcare_clinic.appointment_agent.conversation.SessionService;
import com.brightcare_clinic.appointment_agent.conversation.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final IntentService intentService;
    private final SessionService sessionService;
    private final BookingWorkflowService bookingWorkflowService;

    public String processMessage(Long chatId, String message) {
        UserSession session = sessionService.getSession(chatId);
        String response;

        try {
            response = switch (session.getState()) {
                case WAITING_FOR_SLOT_CONFIRMATION -> handleSlotConfirmation(session, message);
                case WAITING_FOR_EMAIL -> handleEmailCollection(session, message);
                default -> handleByIntent(session, message);
            };
        } catch (IOException e) {
            log.error("Calendar operation failed for chatId {}", session.getChatId(), e);
            response = "Sorry, I couldn't reach the calendar right now. Please try again shortly.";
        }

        sessionService.saveSession(session);
        return response;
    }

    private String handleByIntent(UserSession session, String message) throws IOException {
        IntentResult intentResult = intentService.detectIntent(message);

        return switch (intentResult.getIntentType()) {
            case GREETING -> "Hello! Welcome to BrightCare Clinic. How can I help you today?";
            case BOOK_APPOINTMENT -> startBooking(session);
            case FAQ -> "Let me help answer your question.";
            default -> "I'm not sure I understood that. Could you rephrase?";
        };
    }

    private String startBooking(UserSession session) throws IOException {
        // No date/time extraction from natural language yet (that's Phase 9, Gemini) -
        // assume "tomorrow at 2 PM" as the requested slot for now.
        BookingRequest requestedSlot = new BookingRequest(null, LocalDate.now().plusDays(1), LocalTime.of(14, 0), null);
        BookingResponse bookingResponse = bookingWorkflowService.checkAvailability(requestedSlot);

        session.setState(ConversationState.WAITING_FOR_SLOT_CONFIRMATION);

        if (bookingResponse.getStatus() == BookingStatus.SLOT_AVAILABLE) {
            session.setPendingBooking(requestedSlot);
            return bookingResponse.getMessage() + " Shall I book it?";
        }

        CalendarSlot suggestedSlot = bookingResponse.getSuggestedSlot();
        session.setPendingBooking(new BookingRequest(null, suggestedSlot.getDate(), suggestedSlot.getStartTime(), null));
        return bookingResponse.getMessage() + " Would you like " + suggestedSlot.getDate() + " at " + suggestedSlot.getStartTime() + " instead?";
    }

    private String handleSlotConfirmation(UserSession session, String message) {
        if (message.trim().equalsIgnoreCase("yes")) {
            session.setState(ConversationState.WAITING_FOR_EMAIL);
            return "Great! What's your email address?";
        }
        session.setState(ConversationState.GREETING);
        session.setPendingBooking(null);
        return "No problem, let's start over. How can I help you today?";
    }

    private String handleEmailCollection(UserSession session, String message) throws IOException {
        BookingRequest pendingBooking = session.getPendingBooking();
        pendingBooking.setEmail(message.trim());

        BookingStatus status = bookingWorkflowService.confirmAppointment(pendingBooking);

        session.setState(ConversationState.BOOKING_COMPLETED);
        session.setPendingBooking(null);

        return status == BookingStatus.CONFIRMED
                ? "Thanks! Your appointment has been booked."
                : "Sorry, something went wrong while booking your appointment.";
    }

}
