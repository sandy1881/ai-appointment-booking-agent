package com.brightcare_clinic.appointment_agent.calendar.model;

import com.google.api.services.calendar.CalendarScopes;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public final class CalendarConstants {

    public static final String APPLICATION_NAME = "BrightCare Clinic Appointment Agent";
    public static final String TOKENS_DIRECTORY_PATH = "tokens";
    public static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    public static final int SLOT_DURATION_MINUTES = 30;
    public static final LocalTime BUSINESS_HOURS_START = LocalTime.of(9, 0);
    public static final LocalTime BUSINESS_HOURS_END = LocalTime.of(17, 0);
    public static final int MAX_SLOT_SEARCH_ATTEMPTS = 64;

    private CalendarConstants() {
    }

}
