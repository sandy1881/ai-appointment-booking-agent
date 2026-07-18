package com.brightcare_clinic.appointment_agent.calendar.controller;

import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final GoogleCalendarService calendarService;

    @GetMapping("/test")
    public String test() throws IOException {
        calendarService.getCalendarClient().calendarList().list().execute();
        return "Google Calendar authenticated successfully";
    }

}
