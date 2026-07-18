package com.brightcare_clinic.appointment_agent.ai;

import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.ai.service.GeminiResponseParser;
import com.brightcare_clinic.appointment_agent.ai.service.GeminiService;
import com.brightcare_clinic.appointment_agent.ai.service.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IntentService {

    private final GeminiService geminiService;
    private final PromptBuilder promptBuilder;
    private final GeminiResponseParser geminiResponseParser;

    public IntentResult detectIntent(String message) {
        String prompt = promptBuilder.buildIntentExtractionPrompt(message);
        String rawJson = geminiService.analyzeMessage(prompt);
        return geminiResponseParser.parse(rawJson, message);
    }

}
