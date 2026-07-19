package com.brightcare_clinic.appointment_agent.ai.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class PromptBuilder {

    public String buildIntentExtractionPrompt(String message, List<String> history) {
        return """
                You are an intent classifier and information extractor for a clinic appointment booking assistant.

                %s
                Classify the user's message into exactly one of these intents: GREETING, BOOK_APPOINTMENT, CANCEL_APPOINTMENT, FAQ, CLOSING, GENERAL.
                Use CLOSING for messages that are just ending the conversation with no new request, such as "thanks", "no thanks", "thank you", "that's all", "bye", "nope, that's it".

                If the intent is BOOK_APPOINTMENT or CANCEL_APPOINTMENT, also extract:
                - patientName (string, or null if not mentioned)
                - date (ISO format YYYY-MM-DD, resolved relative to today's date which is %s, or null if not mentioned)
                - time (24-hour format HH:mm, or null if not mentioned)
                - email (string, or null if not mentioned)

                Return ONLY a JSON object with exactly these keys: intent, patientName, date, time, email.

                Current message: "%s"
                """.formatted(historyBlock(history), LocalDate.now(), message);
    }

    public String buildBookingDetailsPrompt(String message, List<String> history) {
        return """
                The user is in the middle of booking a clinic appointment and was just asked what date and time they'd like.

                %s
                Extract the appointment date and time from their reply, resolved relative to today's date which is %s.
                Use the conversation above for context if their reply refers back to something already discussed (e.g. "the later one").

                Return ONLY a JSON object with exactly these keys:
                - date (ISO format YYYY-MM-DD, or null if not mentioned or unclear)
                - time (24-hour format HH:mm, or null if not mentioned or unclear)

                Current message: "%s"
                """.formatted(historyBlock(history), LocalDate.now(), message);
    }

    public String buildCancellationDetailsPrompt(String message, List<String> history) {
        return """
                The user wants to cancel an existing clinic appointment and was just asked what date and time that appointment was booked for.

                %s
                Extract the appointment date and time from their reply, resolved relative to today's date which is %s.
                Use the conversation above for context if their reply refers back to something already discussed (e.g. "the later one").

                Return ONLY a JSON object with exactly these keys:
                - date (ISO format YYYY-MM-DD, or null if not mentioned or unclear)
                - time (24-hour format HH:mm, or null if not mentioned or unclear)

                Current message: "%s"
                """.formatted(historyBlock(history), LocalDate.now(), message);
    }

    private String historyBlock(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        return "Conversation so far:\n" + String.join("\n", history) + "\n";
    }

}
