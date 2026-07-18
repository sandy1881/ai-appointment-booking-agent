package com.brightcare_clinic.appointment_agent.agent;

import com.brightcare_clinic.appointment_agent.ai.IntentService;
import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final IntentService intentService;

    public String processMessage(String message) {
        IntentResult intentResult = intentService.detectIntent(message);

        return switch (intentResult.getIntentType()) {
            case GREETING -> "Hello! Welcome to BrightCare Clinic. How can I help you today?";
            case BOOK_APPOINTMENT -> "Sure, let's get your appointment booked.";
            case FAQ -> "Let me help answer your question.";
            default -> "I'm not sure I understood that. Could you rephrase?";
        };
    }

}
