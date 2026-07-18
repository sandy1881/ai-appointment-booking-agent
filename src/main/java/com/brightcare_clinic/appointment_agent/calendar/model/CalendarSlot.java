package com.brightcare_clinic.appointment_agent.calendar.model;

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
public class CalendarSlot {

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;
    private String message;

}
