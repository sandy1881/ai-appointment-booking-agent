package com.brightcare_clinic.appointment_agent.ai.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PromptBuilder {

    public String buildIntentExtractionPrompt(String message) {
        return """
                You are an intent classifier and information extractor for a clinic appointment booking assistant.

                Classify the user's message into exactly one of these intents: GREETING, BOOK_APPOINTMENT, FAQ, GENERAL.

                If the intent is BOOK_APPOINTMENT, also extract:
                - patientName (string, or null if not mentioned)
                - date (ISO format YYYY-MM-DD, resolved relative to today's date which is %s, or null if not mentioned)
                - time (24-hour format HH:mm, or null if not mentioned)
                - email (string, or null if not mentioned)

                Return ONLY a JSON object with exactly these keys: intent, patientName, date, time, email.

                Message: "%s"
                """.formatted(LocalDate.now(), message);
    }

}
