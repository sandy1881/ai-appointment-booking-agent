package com.brightcare_clinic.appointment_agent.ai;

import com.brightcare_clinic.appointment_agent.ai.model.IntentResult;
import com.brightcare_clinic.appointment_agent.ai.model.IntentType;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class IntentService {

    public IntentResult detectIntent(String message) {
        List<String> words = Arrays.asList(message.toLowerCase().split("\\W+"));
        IntentType intentType;

        if (words.contains("book") || words.contains("appointment")) {
            intentType = IntentType.BOOK_APPOINTMENT;
        } else if (words.contains("hi") || words.contains("hello") || words.contains("hey")) {
            intentType = IntentType.GREETING;
        } else if (words.contains("where") || words.contains("location") || words.contains("hours") || words.contains("address")) {
            intentType = IntentType.FAQ;
        } else {
            intentType = IntentType.GENERAL;
        }

        return new IntentResult(intentType, message);
    }

}
