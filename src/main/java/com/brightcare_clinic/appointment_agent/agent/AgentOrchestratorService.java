package com.brightcare_clinic.appointment_agent.agent;

import com.brightcare_clinic.appointment_agent.ai.IntentService;
import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import com.brightcare_clinic.appointment_agent.ai.model.BookingExtraction;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingResponse;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.booking.BookingWorkflowService;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.conversation.ConversationState;
import com.brightcare_clinic.appointment_agent.conversation.SessionService;
import com.brightcare_clinic.appointment_agent.conversation.UserSession;
import com.brightcare_clinic.appointment_agent.faq.dto.FaqResponse;
import com.brightcare_clinic.appointment_agent.faq.service.FaqService;
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
    private final FaqService faqService;

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
        } catch (GeminiException e) {
            log.error("Gemini call failed for chatId {}", session.getChatId(), e);
            response = "Sorry, I'm having trouble understanding right now. Please try again shortly.";
        }

        sessionService.saveSession(session);
        return response;
    }

    private String handleByIntent(UserSession session, String message) throws IOException {
        FaqResponse faqResponse = faqService.findAnswer(message);
        if (faqResponse.matched()) {
            return faqResponse.answer();
        }

        IntentResult intentResult = intentService.detectIntent(message);

        return switch (intentResult.getIntentType()) {
            case GREETING -> "Hello! Welcome to BrightCare Clinic. How can I help you today?";
            case BOOK_APPOINTMENT -> startBooking(session, intentResult.getBookingExtraction());
            case FAQ -> "I don't have a specific answer for that, but feel free to call the clinic directly.";
            default -> "I'm not sure I understood that. Could you rephrase?";
        };
    }

    private String startBooking(UserSession session, BookingExtraction extraction) throws IOException {
        LocalDate requestedDate = extraction != null && extraction.getDate() != null
                ? extraction.getDate() : LocalDate.now().plusDays(1);
        LocalTime requestedTime = extraction != null && extraction.getTime() != null
                ? extraction.getTime() : LocalTime.of(14, 0);
        String patientName = extraction != null ? extraction.getPatientName() : null;
        String email = extraction != null ? extraction.getEmail() : null;

        BookingRequest requestedSlot = new BookingRequest(patientName, requestedDate, requestedTime, email);
        BookingResponse bookingResponse = bookingWorkflowService.checkAvailability(requestedSlot);

        session.setState(ConversationState.WAITING_FOR_SLOT_CONFIRMATION);

        if (bookingResponse.getStatus() == BookingStatus.SLOT_AVAILABLE) {
            session.setPendingBooking(requestedSlot);
            return bookingResponse.getMessage() + " Shall I book it?";
        }

        CalendarSlot suggestedSlot = bookingResponse.getSuggestedSlot();
        session.setPendingBooking(new BookingRequest(patientName, suggestedSlot.getDate(), suggestedSlot.getStartTime(), email));
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

        BookingResponse response = bookingWorkflowService.confirmAppointment(pendingBooking);

        session.setState(ConversationState.BOOKING_COMPLETED);
        session.setPendingBooking(null);

        return response.getStatus() == BookingStatus.CONFIRMED
                ? response.getMessage()
                : "Sorry, something went wrong while booking your appointment.";
    }

}
