package com.brightcare_clinic.appointment_agent.ai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

    private IntentType intentType;
    private String userMessage;

}
