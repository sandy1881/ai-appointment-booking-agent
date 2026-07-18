package com.brightcare_clinic.appointment_agent.email.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailTemplate {

    private final String subject;
    private final String htmlBody;

}
