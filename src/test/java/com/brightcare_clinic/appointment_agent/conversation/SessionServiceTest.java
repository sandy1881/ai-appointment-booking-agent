package com.brightcare_clinic.appointment_agent.conversation;

import com.brightcare_clinic.appointment_agent.booking.BookingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionServiceTest {

    @TempDir
    private Path tempDir;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private String storePath() {
        return tempDir.resolve("sessions.json").toString();
    }

    @Test
    void getSession_forUnknownChatId_createsFreshGreetingSession() {
        SessionService sessionService = new SessionService(objectMapper, storePath());

        UserSession session = sessionService.getSession(42L);

        assertEquals(42L, session.getChatId());
        assertEquals(ConversationState.GREETING, session.getState());
        assertNull(session.getPendingBooking());
    }

    @Test
    void savedSession_survivesReload_asIfAppRestarted() {
        String storePath = storePath();
        SessionService original = new SessionService(objectMapper, storePath);

        UserSession session = original.getSession(7L);
        session.setState(ConversationState.WAITING_FOR_EMAIL);
        session.setPendingBooking(new BookingRequest("Rohan", LocalDate.of(2026, 8, 3), LocalTime.of(14, 0), null));
        session.recordTurn("book an appointment", "Sure! What date and time would you like to come in?");
        original.saveSession(session);

        // A brand new instance simulates the app restarting. In production, Spring calls
        // the @PostConstruct loadFromDisk() automatically on startup; here there's no
        // container, so we call it directly to simulate exactly that lifecycle step.
        SessionService reloaded = new SessionService(objectMapper, storePath);
        reloaded.loadFromDisk();
        UserSession restored = reloaded.getSession(7L);

        assertNotSame(session, restored);
        assertEquals(ConversationState.WAITING_FOR_EMAIL, restored.getState());
        assertEquals("Rohan", restored.getPendingBooking().getPatientName());
        assertEquals(LocalDate.of(2026, 8, 3), restored.getPendingBooking().getAppointmentDate());
        assertEquals(LocalTime.of(14, 0), restored.getPendingBooking().getAppointmentTime());
        assertEquals(2, restored.getConversationHistory().size());
        assertEquals("User: book an appointment", restored.getConversationHistory().get(0));
    }

    @Test
    void removeSession_removesFromDiskToo() {
        String storePath = storePath();
        SessionService original = new SessionService(objectMapper, storePath);
        original.saveSession(original.getSession(9L));

        original.removeSession(9L);

        SessionService reloaded = new SessionService(objectMapper, storePath);
        reloaded.loadFromDisk();
        UserSession freshSession = reloaded.getSession(9L);

        // Freshly created, not the removed one - it never persisted after removal.
        assertEquals(ConversationState.GREETING, freshSession.getState());
        assertNull(freshSession.getPendingBooking());
    }

}
