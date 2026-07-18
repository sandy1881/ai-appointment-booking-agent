package com.brightcare_clinic.appointment_agent.ai.service;

import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.ai.model.IntentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiResponseParserTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final GeminiResponseParser parser = new GeminiResponseParser(objectMapper);

    @Test
    void parse_bookAppointmentWithAllFields_extractsBookingDetails() {
        String json = """
                {"intent":"book_appointment","patientName":"Rohan","date":"2026-08-01","time":"15:00","email":"rohan@example.com"}
                """;

        IntentResult result = parser.parse(json, "book tomorrow at 3pm");

        assertEquals(IntentType.BOOK_APPOINTMENT, result.getIntentType());
        assertEquals("Rohan", result.getBookingExtraction().getPatientName());
        assertEquals(LocalDate.of(2026, 8, 1), result.getBookingExtraction().getDate());
        assertEquals(LocalTime.of(15, 0), result.getBookingExtraction().getTime());
        assertEquals("rohan@example.com", result.getBookingExtraction().getEmail());
    }

    @Test
    void parse_cancelAppointmentWithDateTime_extractsBookingDetails() {
        String json = """
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-01","time":"15:00","email":null}
                """;

        IntentResult result = parser.parse(json, "cancel my Monday 3pm appointment");

        assertEquals(IntentType.CANCEL_APPOINTMENT, result.getIntentType());
        assertEquals(LocalDate.of(2026, 8, 1), result.getBookingExtraction().getDate());
        assertEquals(LocalTime.of(15, 0), result.getBookingExtraction().getTime());
    }

    @Test
    void parse_greetingIntent_hasNoBookingExtraction() {
        String json = """
                {"intent":"greeting","patientName":null,"date":null,"time":null,"email":null}
                """;

        IntentResult result = parser.parse(json, "hi there");

        assertEquals(IntentType.GREETING, result.getIntentType());
        assertNull(result.getBookingExtraction());
    }

    @Test
    void parse_unknownIntentValue_fallsBackToGeneral() {
        String json = """
                {"intent":"something_unexpected"}
                """;

        IntentResult result = parser.parse(json, "asdf");

        assertEquals(IntentType.GENERAL, result.getIntentType());
    }

    @Test
    void parse_bookAppointmentWithMissingDateTime_hasNullFieldsInsteadOfFailing() {
        String json = """
                {"intent":"book_appointment","patientName":"Rohan"}
                """;

        IntentResult result = parser.parse(json, "I want to book");

        assertEquals(IntentType.BOOK_APPOINTMENT, result.getIntentType());
        assertNull(result.getBookingExtraction().getDate());
        assertNull(result.getBookingExtraction().getTime());
    }

    @Test
    void parse_malformedJson_throwsGeminiException() {
        assertThrows(GeminiException.class, () -> parser.parse("not json at all", "hello"));
    }

}
