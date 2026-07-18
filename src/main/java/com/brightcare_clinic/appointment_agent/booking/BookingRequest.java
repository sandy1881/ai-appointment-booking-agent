package com.brightcare_clinic.appointment_agent.booking;

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
public class BookingRequest {

    private String patientName;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String email;

}
