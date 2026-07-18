package com.brightcare_clinic.appointment_agent.email.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "email")
public class MailConfig {

    private String from;
    private String fromName;

}
