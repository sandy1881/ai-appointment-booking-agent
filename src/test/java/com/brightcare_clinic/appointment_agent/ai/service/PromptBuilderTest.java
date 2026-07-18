package com.brightcare_clinic.appointment_agent.ai.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildIntentExtractionPrompt_includesUserMessage() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("Book an appointment tomorrow at 3pm");

        assertTrue(prompt.contains("Book an appointment tomorrow at 3pm"));
    }

    @Test
    void buildIntentExtractionPrompt_includesTodaysDateForRelativeResolution() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi");

        assertTrue(prompt.contains(LocalDate.now().toString()));
    }

    @Test
    void buildIntentExtractionPrompt_listsAllExpectedIntentTypes() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi");

        assertTrue(prompt.contains("GREETING"));
        assertTrue(prompt.contains("BOOK_APPOINTMENT"));
        assertTrue(prompt.contains("FAQ"));
        assertTrue(prompt.contains("GENERAL"));
    }

    @Test
    void buildIntentExtractionPrompt_requestsJsonOnlyOutput() {
        String prompt = promptBuilder.buildIntentExtractionPrompt("hi");

        assertTrue(prompt.contains("Return ONLY a JSON object"));
    }

}
