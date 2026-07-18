package com.brightcare_clinic.appointment_agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clinic")
public class ClinicProperties {

    private String name;
    private String timezone;

}
