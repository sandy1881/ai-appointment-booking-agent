package com.brightcare_clinic.appointment_agent.booking;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    private int slotDuration;
    private LocalTime businessStart;
    private LocalTime businessEnd;

}
