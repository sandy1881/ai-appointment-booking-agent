package com.brightcare_clinic.appointment_agent.ai;

import com.brightcare_clinic.appointment_agent.ai.model.BookingExtraction;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.ai.service.GeminiResponseParser;
import com.brightcare_clinic.appointment_agent.ai.service.GeminiService;
import com.brightcare_clinic.appointment_agent.ai.service.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private final GeminiService geminiService;
    private final PromptBuilder promptBuilder;
    private final GeminiResponseParser geminiResponseParser;

    public IntentResult detectIntent(String message) {
        String prompt = promptBuilder.buildIntentExtractionPrompt(message);
        String rawJson = geminiService.analyzeMessage(prompt);
        IntentResult result = geminiResponseParser.parse(rawJson, message);
        log.info("Intent detected: {}", result.getIntentType());
        return result;
    }

    public BookingExtraction extractBookingDetails(String message) {
        String prompt = promptBuilder.buildBookingDetailsPrompt(message);
        String rawJson = geminiService.analyzeMessage(prompt);
        return geminiResponseParser.parseBookingDetails(rawJson);
    }

}
