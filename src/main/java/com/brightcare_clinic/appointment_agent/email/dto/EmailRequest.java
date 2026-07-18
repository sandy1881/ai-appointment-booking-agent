package com.brightcare_clinic.appointment_agent.email.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    private String to;
    private String patientName;
    private LocalDate date;
    private LocalTime time;

}
