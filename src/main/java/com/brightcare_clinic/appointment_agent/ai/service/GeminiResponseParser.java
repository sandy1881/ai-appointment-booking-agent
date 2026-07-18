package com.brightcare_clinic.appointment_agent.ai.service;

import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import com.brightcare_clinic.appointment_agent.ai.model.BookingExtraction;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.ai.model.IntentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class GeminiResponseParser {

    private final ObjectMapper objectMapper;

    public IntentResult parse(String rawJson, String originalMessage) {
        JsonNode node = readTree(rawJson);
        IntentType intentType = parseIntentType(textOrNull(node, "intent"));
        BookingExtraction bookingExtraction = intentType == IntentType.BOOK_APPOINTMENT
                ? parseBookingExtraction(node)
                : null;

        return new IntentResult(intentType, originalMessage, bookingExtraction);
    }

    private JsonNode readTree(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JacksonException e) {
            throw new GeminiException("Failed to parse Gemini response as JSON", e);
        }
    }

    private IntentType parseIntentType(String value) {
        if (value == null) {
            return IntentType.GENERAL;
        }
        try {
            return IntentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IntentType.GENERAL;
        }
    }

    private BookingExtraction parseBookingExtraction(JsonNode node) {
        String patientName = textOrNull(node, "patientName");
        String email = textOrNull(node, "email");
        LocalDate date = parseDate(textOrNull(node, "date"));
        LocalTime time = parseTime(textOrNull(node, "time"));
        return new BookingExtraction(patientName, date, time, email);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

}
