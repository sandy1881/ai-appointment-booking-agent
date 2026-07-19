package com.brightcare_clinic.appointment_agent.ai.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildIntentExtractionPrompt_includesUserMessage() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("Book an appointment tomorrow at 3pm", List.of());

        assertTrue(prompt.contains("Book an appointment tomorrow at 3pm"));
    }

    @Test
    void buildIntentExtractionPrompt_includesTodaysDateForRelativeResolution() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi", List.of());

        assertTrue(prompt.contains(LocalDate.now().toString()));
    }

    @Test
    void buildIntentExtractionPrompt_listsAllExpectedIntentTypes() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi", List.of());

        assertTrue(prompt.contains("GREETING"));
        assertTrue(prompt.contains("BOOK_APPOINTMENT"));
        assertTrue(prompt.contains("CANCEL_APPOINTMENT"));
        assertTrue(prompt.contains("FAQ"));
        assertTrue(prompt.contains("CLOSING"));
        assertTrue(prompt.contains("GENERAL"));
    }

    @Test
    void buildIntentExtractionPrompt_requestsJsonOnlyOutput() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi", List.of());

        assertTrue(prompt.contains("Return ONLY a JSON object"));
    }

    @Test
    void buildIntentExtractionPrompt_withEmptyHistory_omitsHistorySection() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi", List.of());

        assertFalse(prompt.contains("Conversation so far"));
    }

    @Test
    void buildIntentExtractionPrompt_withHistory_includesPriorTurns() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("the later one",
                List.of("User: Can I book Monday at 2pm?", "Bot: 2pm isn't available. The nearest opening is 3pm."));

        assertTrue(prompt.contains("Conversation so far"));
        assertTrue(prompt.contains("Can I book Monday at 2pm?"));
        assertTrue(prompt.contains("The nearest opening is 3pm"));
    }

    @Test
    void buildBookingDetailsPrompt_withHistory_includesPriorTurns() {
        String prompt = promptBuilder.buildBookingDetailsPrompt("the later one",
                List.of("Bot: Would you like 3pm or 4pm?"));

        assertTrue(prompt.contains("Would you like 3pm or 4pm?"));
    }

}
