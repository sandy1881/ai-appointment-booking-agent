package com.brightcare_clinic.appointment_agent.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class BookingTimeFormatter {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("h:mm");

    private BookingTimeFormatter() {
    }

    public static String formatTime(LocalTime time) {
        String meridiem = time.getHour() < 12 ? "am" : "pm";
        return time.format(HOUR_MINUTE) + meridiem;
    }

    public static String formatDayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    public static String formatDateTime(LocalDate date, LocalTime time) {
        return formatDayName(date) + " " + formatTime(time);
    }

}
