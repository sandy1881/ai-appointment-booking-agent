package com.brightcare_clinic.appointment_agent.calendar.model;

import com.google.api.services.calendar.CalendarScopes;

import java.util.Collections;
import java.util.List;

public final class CalendarConstants {

    public static final String APPLICATION_NAME = "BrightCare Clinic Appointment Agent";
    public static final String TOKENS_DIRECTORY_PATH = "tokens";
    public static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    private CalendarConstants() {
    }

}
