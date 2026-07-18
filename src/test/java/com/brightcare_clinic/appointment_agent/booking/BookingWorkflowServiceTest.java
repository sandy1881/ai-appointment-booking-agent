package com.brightcare_clinic.appointment_agent.booking;

import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import com.brightcare_clinic.appointment_agent.email.exception.EmailException;
import com.brightcare_clinic.appointment_agent.email.service.EmailService;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingWorkflowServiceTest {

    @Mock
    private GoogleCalendarService googleCalendarService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private BookingWorkflowService bookingWorkflowService;

    @Test
    void checkAvailability_whenOutsideBusinessHours_returnsUnavailableWithoutCallingCalendar() throws Exception {
        LocalDate saturday = LocalDate.of(2026, 8, 1);
        LocalTime time = LocalTime.of(14, 0);
        when(googleCalendarService.isWithinBusinessHours(saturday, time)).thenReturn(false);

        BookingResponse response = bookingWorkflowService.checkAvailability(new BookingRequest(null, saturday, time, null));

        assertEquals(BookingStatus.SLOT_UNAVAILABLE, response.getStatus());
        assertNull(response.getSuggestedSlot());
        verify(googleCalendarService, never()).checkAvailability(any(), any());
    }

    @Test
    void checkAvailability_whenSlotIsAvailable_returnsAvailableStatus() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        when(googleCalendarService.isWithinBusinessHours(date, time)).thenReturn(true);
        when(googleCalendarService.checkAvailability(date, time))
                .thenReturn(new CalendarSlot(date, time, time.plusMinutes(30), true, "Requested slot is available."));

        BookingResponse response = bookingWorkflowService.checkAvailability(new BookingRequest(null, date, time, null));

        assertEquals(BookingStatus.SLOT_AVAILABLE, response.getStatus());
    }

    @Test
    void checkAvailability_whenSlotIsBusy_returnsUnavailableWithSuggestedSlot() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        CalendarSlot suggested = new CalendarSlot(date, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Next available slot found.");

        when(googleCalendarService.isWithinBusinessHours(date, time)).thenReturn(true);
        when(googleCalendarService.checkAvailability(date, time))
                .thenReturn(new CalendarSlot(date, time, time.plusMinutes(30), false, "Requested slot is unavailable."));
        when(googleCalendarService.findNextAvailableSlot(date, time)).thenReturn(suggested);

        BookingResponse response = bookingWorkflowService.checkAvailability(new BookingRequest(null, date, time, null));

        assertEquals(BookingStatus.SLOT_UNAVAILABLE, response.getStatus());
        assertEquals(suggested, response.getSuggestedSlot());
    }

    @Test
    void checkAvailability_whenNoSlotsLeftThatDay_returnsUnavailableWithoutSuggestedSlot() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(17, 30);
        CalendarSlot noneLeft = new CalendarSlot(date, time, time.plusMinutes(30), false, "No available slot found later today.");

        when(googleCalendarService.isWithinBusinessHours(date, time)).thenReturn(true);
        when(googleCalendarService.checkAvailability(date, time))
                .thenReturn(new CalendarSlot(date, time, time.plusMinutes(30), false, "Requested slot is unavailable."));
        when(googleCalendarService.findNextAvailableSlot(date, time)).thenReturn(noneLeft);

        BookingResponse response = bookingWorkflowService.checkAvailability(new BookingRequest(null, date, time, null));

        assertEquals(BookingStatus.SLOT_UNAVAILABLE, response.getStatus());
        assertNull(response.getSuggestedSlot());
    }

    @Test
    void confirmAppointment_sendsConfirmationEmail_whenEmailProvided() throws Exception {
        BookingRequest request = new BookingRequest("Rohan", LocalDate.of(2026, 8, 3), LocalTime.of(14, 0), "rohan@example.com");
        when(googleCalendarService.createAppointment(request)).thenReturn(BookingStatus.CONFIRMED);

        BookingResponse response = bookingWorkflowService.confirmAppointment(request);

        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals("Done — you're booked for Monday 2:00pm. Anything else?", response.getMessage());
        verify(emailService).sendAppointmentConfirmation(any());
    }

    @Test
    void confirmAppointment_stillConfirmsBooking_whenEmailSendingFails() throws Exception {
        BookingRequest request = new BookingRequest("Rohan", LocalDate.of(2026, 8, 3), LocalTime.of(14, 0), "rohan@example.com");
        when(googleCalendarService.createAppointment(request)).thenReturn(BookingStatus.CONFIRMED);
        doThrow(new EmailException("SMTP down", null)).when(emailService).sendAppointmentConfirmation(any());

        BookingResponse response = bookingWorkflowService.confirmAppointment(request);

        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals("Done — you're booked for Monday 2:00pm. We couldn't send the confirmation email, though.", response.getMessage());
    }

    @Test
    void confirmAppointment_whenSlotTakenConcurrently_returnsGracefulMessageWithoutSendingEmail() throws Exception {
        BookingRequest request = new BookingRequest("Rohan", LocalDate.of(2026, 8, 3), LocalTime.of(14, 0), "rohan@example.com");
        when(googleCalendarService.createAppointment(request)).thenReturn(BookingStatus.SLOT_TAKEN);

        BookingResponse response = bookingWorkflowService.confirmAppointment(request);

        assertEquals(BookingStatus.SLOT_TAKEN, response.getStatus());
        assertEquals("Sorry, Monday 2:00pm was just booked by someone else. What other date and time would you like?", response.getMessage());
        verify(emailService, never()).sendAppointmentConfirmation(any());
    }

    @Test
    void confirmAppointment_skipsEmail_whenNoEmailProvided() throws Exception {
        BookingRequest request = new BookingRequest("Rohan", LocalDate.of(2026, 8, 3), LocalTime.of(14, 0), null);
        when(googleCalendarService.createAppointment(request)).thenReturn(BookingStatus.CONFIRMED);

        BookingResponse response = bookingWorkflowService.confirmAppointment(request);

        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        verify(emailService, never()).sendAppointmentConfirmation(any());
    }

    @Test
    void findAppointmentToCancel_whenFound_asksForConfirmation() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        when(googleCalendarService.findEvent(date, time)).thenReturn(Optional.of(new Event().setSummary("Appointment - Rohan")));

        BookingResponse response = bookingWorkflowService.findAppointmentToCancel(date, time);

        assertEquals(BookingStatus.PENDING, response.getStatus());
        assertEquals("Found your appointment for Monday 2:00pm. Shall I cancel it?", response.getMessage());
    }

    @Test
    void findAppointmentToCancel_whenNotFound_asksToDoubleCheck() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        when(googleCalendarService.findEvent(date, time)).thenReturn(Optional.empty());

        BookingResponse response = bookingWorkflowService.findAppointmentToCancel(date, time);

        assertEquals(BookingStatus.NOT_FOUND, response.getStatus());
    }

    @Test
    void cancelAppointment_whenFound_deletesAndSendsCancellationEmail() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        Event event = new Event()
                .setSummary("Appointment - Rohan")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));
        when(googleCalendarService.findEvent(date, time)).thenReturn(Optional.of(event));
        when(googleCalendarService.cancelAppointment(date, time)).thenReturn(BookingStatus.CANCELLED);

        BookingResponse response = bookingWorkflowService.cancelAppointment(date, time);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        assertEquals("Your appointment for Monday 2:00pm has been cancelled.", response.getMessage());
        verify(emailService).sendCancellation(any());
    }

    @Test
    void cancelAppointment_whenNoAttendeeEmail_skipsEmailWithoutFailing() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        Event event = new Event().setSummary("Appointment - Rohan");
        when(googleCalendarService.findEvent(date, time)).thenReturn(Optional.of(event));
        when(googleCalendarService.cancelAppointment(date, time)).thenReturn(BookingStatus.CANCELLED);

        BookingResponse response = bookingWorkflowService.cancelAppointment(date, time);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(emailService, never()).sendCancellation(any());
    }

    @Test
    void cancelAppointment_whenEventDisappearedSinceLookup_returnsNotFoundWithoutCallingCalendarCancel() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        when(googleCalendarService.findEvent(date, time)).thenReturn(Optional.empty());

        BookingResponse response = bookingWorkflowService.cancelAppointment(date, time);

        assertEquals(BookingStatus.NOT_FOUND, response.getStatus());
        verify(googleCalendarService, never()).cancelAppointment(any(), any());
    }

}
