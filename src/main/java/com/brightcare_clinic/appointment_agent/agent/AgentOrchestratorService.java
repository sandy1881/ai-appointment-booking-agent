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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final IntentService intentService;
    private final SessionService sessionService;
    private final BookingWorkflowService bookingWorkflowService;
    private final FaqService faqService;
    private final Validator validator;

    // "yes" alone is too strict for how people actually reply - "yes please", "yeah", "sure",
    // etc. all mean the same thing but fail an exact-equality check. Word-level, not a raw
    // substring check, so this doesn't fire on unrelated words that happen to contain these.
    private static final Set<String> AFFIRMATIVE_WORDS = Set.of("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "confirm", "correct");

    // Cancellation confirmation ("Shall I cancel it?") gets natural replies like "cancel" or
    // "cancel it" that aren't affirmative in the booking-confirmation sense - "cancel" alone would
    // mean decline if said to "Shall I book it?", so this is kept separate from AFFIRMATIVE_WORDS
    // rather than adding "cancel" there and breaking the booking-confirmation flow.
    private static final Set<String> CANCELLATION_CONFIRMATION_WORDS = Set.of("cancel", "delete", "remove");

    // Guards CANCELLATION_CONFIRMATION_WORDS against negated replies like "no, don't cancel it" -
    // without this, the word "cancel" in that sentence would be misread as confirming the
    // cancellation instead of declining it. ("don't" splits into "don"/"t" on \W+.)
    private static final Set<String> NEGATIVE_WORDS = Set.of("no", "nope", "not", "don", "never", "keep");

    private static final String GREETING_RESPONSE = "Hello! Welcome to BrightCare Clinic. How can I help you today?";

    // Matched only when the WHOLE message (after stripping punctuation) is one of these - a bare
    // "hi" doesn't need a Gemini round trip. Anything longer or combined with real content (e.g.
    // "hi, book me tomorrow at 3pm") still goes through Gemini so the actual intent isn't lost.
    private static final Set<String> GREETING_PHRASES = Set.of(
            "hi", "hello", "hey", "hiya", "howdy", "yo", "greetings",
            "hi there", "hello there", "hey there",
            "good morning", "good afternoon", "good evening");

    // Covers casual/typo'd stretching of the single-word greetings above - "hii", "hiii", "heyy",
    // "hellooo" - without hardcoding every letter-repeat variant by hand.
    private static final java.util.regex.Pattern GREETING_PATTERN =
            java.util.regex.Pattern.compile("^(h+i+|h+e+y+|h+e+l+o+|h+i+y+a+|h+o+w+d+y+|y+o+)$");

    private boolean isPureGreeting(String message) {
        String normalized = message.toLowerCase().replaceAll("[^a-z ]", " ").trim().replaceAll("\\s+", " ");
        return GREETING_PHRASES.contains(normalized) || GREETING_PATTERN.matcher(normalized).matches();
    }

    private List<String> words(String message) {
        return Arrays.asList(message.toLowerCase().split("\\W+"));
    }

    private boolean isAffirmative(String message) {
        return words(message).stream().anyMatch(AFFIRMATIVE_WORDS::contains);
    }

    private boolean confirmsCancellation(String message) {
        List<String> tokens = words(message);
        if (tokens.stream().anyMatch(NEGATIVE_WORDS::contains)) {
            return false;
        }
        return tokens.stream().anyMatch(AFFIRMATIVE_WORDS::contains) || tokens.stream().anyMatch(CANCELLATION_CONFIRMATION_WORDS::contains);
    }

    public String processMessage(Long chatId, String message) {
        UserSession session = sessionService.getSession(chatId);
        String response;

        try {
            response = switch (session.getState()) {
                case WAITING_FOR_BOOKING_DETAILS -> handleBookingDetailsCollection(session, message);
                case WAITING_FOR_SLOT_CONFIRMATION -> handleSlotConfirmation(session, message);
                case WAITING_FOR_NAME -> handleNameCollection(session, message);
                case WAITING_FOR_EMAIL -> handleEmailCollection(session, message);
                case WAITING_FOR_CANCELLATION_DETAILS -> handleCancellationDetailsCollection(session, message);
                case WAITING_FOR_CANCELLATION_CONFIRMATION -> handleCancellationConfirmation(session, message);
                default -> handleByIntent(session, message);
            };
        } catch (IOException e) {
            log.error("Calendar operation failed for chatId {}", session.getChatId(), e);
            response = "Sorry, I couldn't reach the calendar right now. Please try again shortly.";
        } catch (GeminiException e) {
            log.error("Gemini call failed for chatId {}", session.getChatId(), e);
            response = "Sorry, I'm having trouble understanding right now. Please try again shortly.";
        } catch (RuntimeException e) {
            log.error("Unexpected error while processing message for chatId {}", session.getChatId(), e);
            response = "Sorry, something went wrong on our end. Please try again shortly.";
        }

        session.recordTurn(message, response);
        sessionService.saveSession(session);
        return response;
    }

    private String handleByIntent(UserSession session, String message) throws IOException {
        if (isPureGreeting(message)) {
            return GREETING_RESPONSE;
        }

        FaqResponse faqResponse = faqService.findAnswer(message);
        if (faqResponse.matched()) {
            return faqResponse.answer();
        }

        IntentResult intentResult = intentService.detectIntent(message, session.getConversationHistory());

        return switch (intentResult.getIntentType()) {
            case GREETING -> GREETING_RESPONSE;
            case BOOK_APPOINTMENT -> startBooking(session, intentResult.getBookingExtraction());
            case CANCEL_APPOINTMENT -> startCancellation(session, intentResult.getBookingExtraction());
            case FAQ -> "I don't have a specific answer for that, but feel free to call the clinic directly.";
            case CLOSING -> "You're welcome! Have a great day.";
            default -> "I'm not sure I understood that. Could you rephrase?";
        };
    }

    private String startBooking(UserSession session, BookingExtraction extraction) throws IOException {
        String patientName = extraction != null ? extraction.getPatientName() : null;
        String email = extraction != null ? extraction.getEmail() : null;

        if (extraction == null || extraction.getDate() == null || extraction.getTime() == null) {
            session.setPendingBooking(new BookingRequest(patientName, null, null, email));
            session.setState(ConversationState.WAITING_FOR_BOOKING_DETAILS);
            return "Sure! What date and time would you like to come in?";
        }

        BookingRequest requestedSlot = new BookingRequest(patientName, extraction.getDate(), extraction.getTime(), email);
        return proceedWithAvailabilityCheck(session, requestedSlot);
    }

    private String handleBookingDetailsCollection(UserSession session, String message) throws IOException {
        BookingExtraction details = intentService.extractBookingDetails(message, session.getConversationHistory());

        if (details.getDate() == null || details.getTime() == null) {
            return "Sorry, I didn't catch a specific date and time. Could you tell me when you'd like to come in? For example: \"tomorrow at 3 PM\".";
        }

        BookingRequest pending = session.getPendingBooking();
        pending.setAppointmentDate(details.getDate());
        pending.setAppointmentTime(details.getTime());

        return proceedWithAvailabilityCheck(session, pending);
    }

    private String proceedWithAvailabilityCheck(UserSession session, BookingRequest requestedSlot) throws IOException {
        BookingResponse bookingResponse = bookingWorkflowService.checkAvailability(requestedSlot);

        if (bookingResponse.getStatus() == BookingStatus.SLOT_AVAILABLE) {
            session.setPendingBooking(requestedSlot);
            session.setState(ConversationState.WAITING_FOR_SLOT_CONFIRMATION);
            return bookingResponse.getMessage() + " Shall I book it?";
        }

        CalendarSlot suggestedSlot = bookingResponse.getSuggestedSlot();
        if (suggestedSlot == null) {
            // Outside business hours, or nothing left that day - ask for a different date/time instead of a yes/no.
            session.setPendingBooking(new BookingRequest(requestedSlot.getPatientName(), null, null, requestedSlot.getEmail()));
            session.setState(ConversationState.WAITING_FOR_BOOKING_DETAILS);
            return bookingResponse.getMessage();
        }

        session.setPendingBooking(new BookingRequest(requestedSlot.getPatientName(), suggestedSlot.getDate(), suggestedSlot.getStartTime(), requestedSlot.getEmail()));
        session.setState(ConversationState.WAITING_FOR_SLOT_CONFIRMATION);
        return bookingResponse.getMessage() + " Shall I book that?";
    }

    private String handleSlotConfirmation(UserSession session, String message) {
        if (isAffirmative(message)) {
            String patientName = session.getPendingBooking().getPatientName();
            if (patientName == null || patientName.isBlank()) {
                session.setState(ConversationState.WAITING_FOR_NAME);
                return "Great! What's your name?";
            }
            session.setState(ConversationState.WAITING_FOR_EMAIL);
            return "Great! What's your email address?";
        }
        session.setState(ConversationState.GREETING);
        session.setPendingBooking(null);
        return "No problem, let's start over. How can I help you today?";
    }

    private String handleNameCollection(UserSession session, String message) {
        String name = message.trim();

        BookingRequest pendingBooking = session.getPendingBooking();
        pendingBooking.setPatientName(name);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validateProperty(pendingBooking, "patientName");
        if (!violations.isEmpty()) {
            return "Sorry, I didn't catch that. Could you tell me your name?";
        }

        session.setState(ConversationState.WAITING_FOR_EMAIL);
        return "Thanks, " + name + "! What's your email address?";
    }

    private String handleEmailCollection(UserSession session, String message) throws IOException {
        String email = message.trim();
        BookingRequest pendingBooking = session.getPendingBooking();
        pendingBooking.setEmail(email);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validateProperty(pendingBooking, "email");
        if (email.isEmpty() || !violations.isEmpty()) {
            return "That doesn't look like a valid email address. Could you double-check and send it again?";
        }

        BookingResponse response = bookingWorkflowService.confirmAppointment(pendingBooking);

        if (response.getStatus() == BookingStatus.CONFIRMED) {
            session.setState(ConversationState.BOOKING_COMPLETED);
            session.setPendingBooking(null);
            return response.getMessage();
        }

        if (response.getStatus() == BookingStatus.SLOT_TAKEN) {
            // Someone else grabbed this slot between the check and the write - ask for a new
            // time rather than falsely reporting a completed booking.
            session.setPendingBooking(new BookingRequest(pendingBooking.getPatientName(), null, null, pendingBooking.getEmail()));
            session.setState(ConversationState.WAITING_FOR_BOOKING_DETAILS);
            return response.getMessage();
        }

        session.setState(ConversationState.GREETING);
        session.setPendingBooking(null);
        return "Sorry, something went wrong while booking your appointment.";
    }

    private String startCancellation(UserSession session, BookingExtraction extraction) throws IOException {
        if (extraction == null || extraction.getDate() == null || extraction.getTime() == null) {
            session.setPendingBooking(null);
            session.setState(ConversationState.WAITING_FOR_CANCELLATION_DETAILS);
            return "Sure, what date and time was your appointment?";
        }

        return lookUpAppointmentToCancel(session, extraction.getDate(), extraction.getTime());
    }

    private String handleCancellationDetailsCollection(UserSession session, String message) throws IOException {
        BookingExtraction details = intentService.extractCancellationDetails(message, session.getConversationHistory());

        if (details.getDate() == null || details.getTime() == null) {
            return "Sorry, I didn't catch a specific date and time. Could you tell me when the appointment was? For example: \"Monday at 2pm\".";
        }

        return lookUpAppointmentToCancel(session, details.getDate(), details.getTime());
    }

    private String lookUpAppointmentToCancel(UserSession session, LocalDate date, LocalTime time) throws IOException {
        BookingResponse response = bookingWorkflowService.findAppointmentToCancel(date, time);

        if (response.getStatus() == BookingStatus.NOT_FOUND) {
            session.setPendingBooking(null);
            session.setState(ConversationState.WAITING_FOR_CANCELLATION_DETAILS);
            return response.getMessage();
        }

        session.setPendingBooking(new BookingRequest(null, date, time, null));
        session.setState(ConversationState.WAITING_FOR_CANCELLATION_CONFIRMATION);
        return response.getMessage();
    }

    private String handleCancellationConfirmation(UserSession session, String message) throws IOException {
        if (!confirmsCancellation(message)) {
            session.setState(ConversationState.GREETING);
            session.setPendingBooking(null);
            return "No problem, your appointment is still scheduled. Anything else?";
        }

        BookingRequest pending = session.getPendingBooking();
        BookingResponse response = bookingWorkflowService.cancelAppointment(pending.getAppointmentDate(), pending.getAppointmentTime());

        session.setState(ConversationState.GREETING);
        session.setPendingBooking(null);
        return response.getMessage();
    }

}
