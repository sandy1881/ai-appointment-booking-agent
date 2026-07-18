package com.brightcare_clinic.appointment_agent.ai.model;

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
public class BookingExtraction {

    private String patientName;
    private LocalDate date;
    private LocalTime time;
    private String email;

}
