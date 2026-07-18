package com.brightcare_clinic.appointment_agent.agent;

import org.springframework.stereotype.Service;

@Service
public class AgentOrchestratorService {

    public String processMessage(String message) {
        return "Hello! Welcome to BrightCare Clinic.";
    }

}
