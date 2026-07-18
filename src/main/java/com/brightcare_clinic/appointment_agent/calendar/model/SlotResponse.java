package com.brightcare_clinic.appointment_agent.calendar.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponse(LocalDate date, LocalTime start, LocalTime end) {
}
