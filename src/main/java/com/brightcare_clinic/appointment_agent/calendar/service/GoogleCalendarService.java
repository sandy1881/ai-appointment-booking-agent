package com.brightcare_clinic.appointment_agent.calendar.service;

import com.google.api.services.calendar.Calendar;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final ObjectProvider<Calendar> calendarClientProvider;

    public Calendar getCalendarClient() {
        return calendarClientProvider.getObject();
    }

}
