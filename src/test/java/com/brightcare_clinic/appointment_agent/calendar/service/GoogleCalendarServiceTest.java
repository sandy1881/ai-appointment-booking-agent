package com.brightcare_clinic.appointment_agent.calendar.service;

import com.brightcare_clinic.appointment_agent.booking.BookingProperties;
import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock
    private ObjectProvider<Calendar> calendarClientProvider;

    @Mock
    private Calendar calendar;

    @Mock
    private Calendar.Events eventsResource;

    @Mock
    private Calendar.Events.List listRequest;

    @Mock
    private Events eventsResult;

    private BookingProperties bookingProperties;
    private ClinicProperties clinicProperties;
    private GoogleCalendarService googleCalendarService;

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setSlotDuration(30);
        bookingProperties.setBusinessStart(LocalTime.of(9, 0));
        bookingProperties.setBusinessEnd(LocalTime.of(18, 0));

        clinicProperties = new ClinicProperties();
        clinicProperties.setName("BrightCare Clinic");
        clinicProperties.setTimezone("Asia/Kolkata");

        googleCalendarService = new GoogleCalendarService(calendarClientProvider, bookingProperties, clinicProperties);

        org.mockito.Mockito.lenient().when(calendarClientProvider.getObject()).thenReturn(calendar);
    }

    private void stubEventsList(Events... results) throws Exception {
        when(calendar.events()).thenReturn(eventsResource);
        when(eventsResource.list(anyString())).thenReturn(listRequest);
        when(listRequest.setTimeMin(any())).thenReturn(listRequest);
        when(listRequest.setTimeMax(any())).thenReturn(listRequest);
        when(listRequest.setSingleEvents(any())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(results[0], results);
    }

    @Test
    void checkAvailability_whenNoConflictingEvents_returnsAvailable() throws Exception {
        when(eventsResult.getItems()).thenReturn(List.of());
        stubEventsList(eventsResult);

        CalendarSlot slot = googleCalendarService.checkAvailability(LocalDate.of(2026, 8, 1), LocalTime.of(14, 0));

        assertTrue(slot.isAvailable());
        assertEquals("Requested slot is available.", slot.getMessage());
    }

    @Test
    void checkAvailability_whenConflictingEventExists_returnsUnavailable() throws Exception {
        when(eventsResult.getItems()).thenReturn(List.of(new Event()));
        stubEventsList(eventsResult);

        CalendarSlot slot = googleCalendarService.checkAvailability(LocalDate.of(2026, 8, 1), LocalTime.of(14, 0));

        assertFalse(slot.isAvailable());
        assertEquals("Requested slot is unavailable.", slot.getMessage());
    }

    @Test
    void findNextAvailableSlot_skipsBusySlotAndReturnsNextFreeOne() throws Exception {
        Events busy = mock(Events.class);
        when(busy.getItems()).thenReturn(List.of(new Event()));
        Events free = mock(Events.class);
        when(free.getItems()).thenReturn(List.of());

        when(calendar.events()).thenReturn(eventsResource);
        when(eventsResource.list(anyString())).thenReturn(listRequest);
        when(listRequest.setTimeMin(any())).thenReturn(listRequest);
        when(listRequest.setTimeMax(any())).thenReturn(listRequest);
        when(listRequest.setSingleEvents(any())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(busy, free);

        CalendarSlot slot = googleCalendarService.findNextAvailableSlot(LocalDate.of(2026, 8, 1), LocalTime.of(14, 0));

        assertTrue(slot.isAvailable());
        assertEquals(LocalDate.of(2026, 8, 1), slot.getDate());
        assertEquals(LocalTime.of(15, 0), slot.getStartTime());
        assertEquals(LocalTime.of(15, 30), slot.getEndTime());
    }

    @Test
    void findNextAvailableSlot_whenNothingFreeForRestOfDay_doesNotRollOverToNextDay() throws Exception {
        Events busy = mock(Events.class);
        when(busy.getItems()).thenReturn(List.of(new Event()));

        when(calendar.events()).thenReturn(eventsResource);
        when(eventsResource.list(anyString())).thenReturn(listRequest);
        when(listRequest.setTimeMin(any())).thenReturn(listRequest);
        when(listRequest.setTimeMax(any())).thenReturn(listRequest);
        when(listRequest.setSingleEvents(any())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(busy);

        // Requesting 17:00 -> the only remaining same-day candidate is 17:30 (the last slot
        // before 18:00 close). That candidate is busy, so there must be no slot left that
        // day - and crucially, this must NOT roll over into the next day.
        CalendarSlot slot = googleCalendarService.findNextAvailableSlot(LocalDate.of(2026, 8, 1), LocalTime.of(17, 0));

        assertFalse(slot.isAvailable());
        assertEquals(LocalDate.of(2026, 8, 1), slot.getDate());
    }

    @Test
    void findNextAvailableSlot_whenRequestedTimeIsLastSlotOfDay_returnsUnavailableWithoutCallingCalendar() throws Exception {
        // Requesting 17:30 (the last slot of the day itself) leaves no later candidate at all -
        // the search loop must exit on its very first check, without even calling the calendar.
        CalendarSlot slot = googleCalendarService.findNextAvailableSlot(LocalDate.of(2026, 8, 1), LocalTime.of(17, 30));

        assertFalse(slot.isAvailable());
        assertEquals(LocalDate.of(2026, 8, 1), slot.getDate());
        verifyNoInteractions(calendar);
    }

    @Test
    void isWithinBusinessHours_forWeekdayDuringHours_returnsTrue() {
        assertTrue(googleCalendarService.isWithinBusinessHours(LocalDate.of(2026, 8, 3), LocalTime.of(14, 0)));
    }

    @Test
    void isWithinBusinessHours_forWeekend_returnsFalse() {
        LocalDate saturday = LocalDate.of(2026, 8, 1);
        assertFalse(googleCalendarService.isWithinBusinessHours(saturday, LocalTime.of(14, 0)));
    }

    @Test
    void isWithinBusinessHours_beforeOpening_returnsFalse() {
        assertFalse(googleCalendarService.isWithinBusinessHours(LocalDate.of(2026, 8, 3), LocalTime.of(8, 0)));
    }

    @Test
    void isWithinBusinessHours_tooCloseToClosingToFitSlot_returnsFalse() {
        assertFalse(googleCalendarService.isWithinBusinessHours(LocalDate.of(2026, 8, 3), LocalTime.of(17, 45)));
    }

    @Test
    void createAppointment_insertsEventAndReturnsConfirmed() throws Exception {
        when(eventsResult.getItems()).thenReturn(List.of());
        stubEventsList(eventsResult);

        Calendar.Events.Insert insertRequest = mock(Calendar.Events.Insert.class);
        Event insertedEvent = new Event();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventsResource.insert(eq("primary"), eventCaptor.capture())).thenReturn(insertRequest);
        when(insertRequest.execute()).thenReturn(insertedEvent);

        BookingRequest request = new BookingRequest("Rohan", LocalDate.of(2026, 8, 1), LocalTime.of(15, 0), "rohan@example.com");
        BookingStatus status = googleCalendarService.createAppointment(request);

        assertEquals(BookingStatus.CONFIRMED, status);
        assertEquals("Appointment - Rohan", eventCaptor.getValue().getSummary());
        verify(insertRequest).execute();
    }

    @Test
    void createAppointment_whenSlotBecameConflictedBeforeWrite_returnsSlotTakenWithoutInserting() throws Exception {
        // The initial availability check may have happened turns ago in the conversation;
        // simulate someone else having taken the slot in the meantime.
        when(eventsResult.getItems()).thenReturn(List.of(new Event()));
        stubEventsList(eventsResult);

        BookingRequest request = new BookingRequest("Rohan", LocalDate.of(2026, 8, 1), LocalTime.of(15, 0), "rohan@example.com");
        BookingStatus status = googleCalendarService.createAppointment(request);

        assertEquals(BookingStatus.SLOT_TAKEN, status);
        verify(eventsResource, never()).insert(anyString(), any(Event.class));
    }

    @Test
    void createAppointment_underConcurrentRequestsForSameSlot_onlyOneSucceeds() throws Exception {
        AtomicBoolean booked = new AtomicBoolean(false);

        when(calendar.events()).thenReturn(eventsResource);
        when(eventsResource.list(anyString())).thenReturn(listRequest);
        when(listRequest.setTimeMin(any())).thenReturn(listRequest);
        when(listRequest.setTimeMax(any())).thenReturn(listRequest);
        when(listRequest.setSingleEvents(any())).thenReturn(listRequest);
        when(listRequest.execute()).thenAnswer(invocation -> {
            // Widen the race window: without the lock, both threads could read "free" here
            // before either one has written its event.
            Thread.sleep(20);
            Events result = mock(Events.class);
            when(result.getItems()).thenReturn(booked.get() ? List.of(new Event()) : List.of());
            return result;
        });

        Calendar.Events.Insert insertRequest = mock(Calendar.Events.Insert.class);
        when(eventsResource.insert(anyString(), any(Event.class))).thenAnswer(invocation -> {
            booked.set(true);
            return insertRequest;
        });
        when(insertRequest.execute()).thenReturn(new Event());

        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalTime time = LocalTime.of(14, 0);
        BookingRequest requestA = new BookingRequest("Alice", date, time, "alice@example.com");
        BookingRequest requestB = new BookingRequest("Bob", date, time, "bob@example.com");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<BookingStatus> taskA = () -> {
            startLatch.await();
            return googleCalendarService.createAppointment(requestA);
        };
        Callable<BookingStatus> taskB = () -> {
            startLatch.await();
            return googleCalendarService.createAppointment(requestB);
        };

        Future<BookingStatus> futureA = executor.submit(taskA);
        Future<BookingStatus> futureB = executor.submit(taskB);
        startLatch.countDown();

        BookingStatus resultA = futureA.get(5, TimeUnit.SECONDS);
        BookingStatus resultB = futureB.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        List<BookingStatus> results = List.of(resultA, resultB);
        assertEquals(1, results.stream().filter(r -> r == BookingStatus.CONFIRMED).count(), "Exactly one request should succeed");
        assertEquals(1, results.stream().filter(r -> r == BookingStatus.SLOT_TAKEN).count(), "The other should be told the slot was taken");
    }

    @Test
    void findEvent_whenEventExists_returnsIt() throws Exception {
        Event existing = new Event().setId("abc123").setSummary("Appointment - Rohan");
        when(eventsResult.getItems()).thenReturn(List.of(existing));
        stubEventsList(eventsResult);

        java.util.Optional<Event> found = googleCalendarService.findEvent(LocalDate.of(2026, 8, 3), LocalTime.of(14, 0));

        assertTrue(found.isPresent());
        assertEquals("abc123", found.get().getId());
    }

    @Test
    void findEvent_whenNoEventExists_returnsEmpty() throws Exception {
        when(eventsResult.getItems()).thenReturn(List.of());
        stubEventsList(eventsResult);

        java.util.Optional<Event> found = googleCalendarService.findEvent(LocalDate.of(2026, 8, 3), LocalTime.of(14, 0));

        assertTrue(found.isEmpty());
    }

    @Test
    void cancelAppointment_whenEventExists_deletesItAndReturnsCancelled() throws Exception {
        Event existing = new Event().setId("abc123").setSummary("Appointment - Rohan");
        when(eventsResult.getItems()).thenReturn(List.of(existing));
        stubEventsList(eventsResult);

        Calendar.Events.Delete deleteRequest = mock(Calendar.Events.Delete.class);
        when(eventsResource.delete(eq("primary"), eq("abc123"))).thenReturn(deleteRequest);

        BookingStatus status = googleCalendarService.cancelAppointment(LocalDate.of(2026, 8, 3), LocalTime.of(14, 0));

        assertEquals(BookingStatus.CANCELLED, status);
        verify(deleteRequest).execute();
    }

    @Test
    void cancelAppointment_whenNoEventExists_returnsNotFoundWithoutDeleting() throws Exception {
        when(eventsResult.getItems()).thenReturn(List.of());
        stubEventsList(eventsResult);

        BookingStatus status = googleCalendarService.cancelAppointment(LocalDate.of(2026, 8, 3), LocalTime.of(14, 0));

        assertEquals(BookingStatus.NOT_FOUND, status);
        verify(eventsResource, never()).delete(anyString(), anyString());
    }

}
