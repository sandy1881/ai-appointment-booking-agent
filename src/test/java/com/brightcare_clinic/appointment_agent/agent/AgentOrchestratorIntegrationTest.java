package com.brightcare_clinic.appointment_agent.agent;

import com.brightcare_clinic.appointment_agent.ai.IntentService;
import com.brightcare_clinic.appointment_agent.ai.service.GeminiResponseParser;
import com.brightcare_clinic.appointment_agent.ai.service.GeminiService;
import com.brightcare_clinic.appointment_agent.ai.service.PromptBuilder;
import com.brightcare_clinic.appointment_agent.booking.BookingStatus;
import com.brightcare_clinic.appointment_agent.booking.BookingWorkflowService;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarSlot;
import com.brightcare_clinic.appointment_agent.calendar.service.GoogleCalendarService;
import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import com.brightcare_clinic.appointment_agent.conversation.SessionService;
import com.brightcare_clinic.appointment_agent.email.config.MailConfig;
import com.brightcare_clinic.appointment_agent.email.service.EmailService;
import com.brightcare_clinic.appointment_agent.email.service.EmailTemplateService;
import com.brightcare_clinic.appointment_agent.faq.repository.FaqRepository;
import com.brightcare_clinic.appointment_agent.faq.service.FaqService;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wires the real service chain together (Intent -> Booking -> Calendar -> Email) and
 * mocks only the true external boundaries: the Gemini HTTP call, the Google Calendar API,
 * and the SMTP send. Deliberately avoids @SpringBootTest so the test stays hermetic -
 * a full context load would also register the Telegram bot against the real Telegram API.
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorIntegrationTest {

    @Mock
    private GeminiService geminiService;

    @Mock
    private GoogleCalendarService googleCalendarService;

    @Mock
    private JavaMailSender mailSender;

    @TempDir
    private Path tempDir;

    private AgentOrchestratorService agentOrchestratorService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        IntentService intentService = new IntentService(geminiService, new PromptBuilder(), new GeminiResponseParser(objectMapper));

        FaqRepository faqRepository = org.mockito.Mockito.mock(FaqRepository.class);
        // lenient: the greeting fast path in AgentOrchestratorService returns before FaqService is
        // ever consulted, so this stub goes unused by greeting-only tests.
        org.mockito.Mockito.lenient().when(faqRepository.findAll()).thenReturn(List.of());
        FaqService faqService = new FaqService(faqRepository);

        ClinicProperties clinicProperties = new ClinicProperties();
        clinicProperties.setName("BrightCare Clinic");
        clinicProperties.setTimezone("Asia/Kolkata");
        EmailTemplateService emailTemplateService = new EmailTemplateService(clinicProperties);

        MailConfig mailConfig = new MailConfig();
        mailConfig.setFrom("clinic@example.com");
        mailConfig.setFromName("BrightCare Clinic");
        org.mockito.Mockito.lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        EmailService emailService = new EmailService(mailSender, emailTemplateService, mailConfig);

        BookingWorkflowService bookingWorkflowService = new BookingWorkflowService(googleCalendarService, emailService);

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SessionService sessionService = new SessionService(objectMapper, tempDir.resolve("sessions.json").toString());
        agentOrchestratorService = new AgentOrchestratorService(intentService, sessionService, bookingWorkflowService, faqService, validator);
    }

    @Test
    void fullBookingFlow_fromMessageToCalendarEventAndEmail() throws Exception {
        Long chatId = 555000L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Rohan","date":"%s","time":"15:00","email":"rohan@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));
        when(googleCalendarService.createAppointment(any())).thenReturn(BookingStatus.CONFIRMED);

        String bookingReply = agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm, my name is Rohan, email rohan@example.com");
        assertTrue(bookingReply.contains("available"));

        // Name was already given upfront, so slot confirmation should skip straight to email
        // rather than asking again.
        String confirmationReply = agentOrchestratorService.processMessage(chatId, "yes");
        assertEquals("Great! What's your email address?", confirmationReply);

        String finalReply = agentOrchestratorService.processMessage(chatId, "rohan@example.com");
        assertTrue(finalReply.contains("you're booked"));

        verify(googleCalendarService).createAppointment(any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void slotConfirmation_acceptsNaturalAffirmativePhrasing_notJustBareYes() throws Exception {
        Long chatId = 555003L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Sandy","date":"%s","time":"17:00","email":"sandy@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(17, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(17, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(17, 0), LocalTime.of(17, 30), true, "Requested slot is available."));

        agentOrchestratorService.processMessage(chatId, "book an appointment tomorrow at 5pm");

        // Regression test: "yes please" used to fall through to the decline branch because
        // handleSlotConfirmation only accepted the exact string "yes".
        String confirmationReply = agentOrchestratorService.processMessage(chatId, "yes please");

        assertEquals("Great! What's your email address?", confirmationReply);
    }

    @Test
    void bookingFlow_alwaysAsksForNameBeforeEmail_evenWhenNotVolunteeredUpfront() throws Exception {
        Long chatId = 555004L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":null,"date":"%s","time":"15:00","email":null}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));
        when(googleCalendarService.createAppointment(any())).thenReturn(BookingStatus.CONFIRMED);

        // Regression test: previously the bot never asked for the patient's name unless it
        // happened to be volunteered upfront, so createAppointment could receive a null name
        // and the calendar event summary became "Appointment - null".
        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm");

        String confirmationReply = agentOrchestratorService.processMessage(chatId, "yes");
        assertEquals("Great! What's your name?", confirmationReply);

        String nameReply = agentOrchestratorService.processMessage(chatId, "Priya");
        assertEquals("Thanks, Priya! What's your email address?", nameReply);

        agentOrchestratorService.processMessage(chatId, "priya@example.com");

        org.mockito.ArgumentCaptor<com.brightcare_clinic.appointment_agent.booking.BookingRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.brightcare_clinic.appointment_agent.booking.BookingRequest.class);
        verify(googleCalendarService).createAppointment(captor.capture());
        assertEquals("Priya", captor.getValue().getPatientName());
    }

    @Test
    void bookingFlow_whenSlotBusy_offersNextAvailableSlotBeforeConfirming() throws Exception {
        Long chatId = 555001L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalTime suggestedTime = LocalTime.of(16, 0);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"15:00","email":"priya@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), false, "Requested slot is unavailable."));
        when(googleCalendarService.findNextAvailableSlot(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, suggestedTime, suggestedTime.plusMinutes(30), true, "Next available slot found."));

        String reply = agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm, my name is Priya, email priya@example.com");

        assertTrue(reply.contains("4:00pm"));
        verify(googleCalendarService, org.mockito.Mockito.never()).createAppointment(any());
    }

    @Test
    void afterBooking_closingRemark_getsGracefulReplyNotRephraseFallback() throws Exception {
        Long chatId = 555005L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString()))
                .thenReturn("""
                        {"intent":"book_appointment","patientName":"Sandy","date":"%s","time":"16:00","email":"sandy@example.com"}
                        """.formatted(tomorrow))
                .thenReturn("""
                        {"intent":"closing","patientName":null,"date":null,"time":null,"email":null}
                        """);
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(16, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(16, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(16, 0), LocalTime.of(16, 30), true, "Requested slot is available."));
        when(googleCalendarService.createAppointment(any())).thenReturn(BookingStatus.CONFIRMED);

        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 4pm, my name is Sandy, email sandy@example.com");
        agentOrchestratorService.processMessage(chatId, "yes");
        agentOrchestratorService.processMessage(chatId, "sandy@example.com");

        // Regression test: "no thanks" after a completed booking used to hit the GENERAL
        // fallback ("I'm not sure I understood that...") instead of just acknowledging.
        String closingReply = agentOrchestratorService.processMessage(chatId, "no thanks");
        assertEquals("You're welcome! Have a great day.", closingReply);
    }

    @Test
    void cancellationFlow_findsAppointmentThenCancelsAndEmailsOnConfirmation() throws Exception {
        Long chatId = 555002L;
        LocalDate monday = LocalDate.of(2026, 8, 3);
        Event existing = new Event()
                .setSummary("Appointment - Rohan")
                .setId("evt-1")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-03","time":"14:00","email":null}
                """);
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0))).thenReturn(Optional.of(existing));
        when(googleCalendarService.cancelAppointment(monday, LocalTime.of(14, 0))).thenReturn(BookingStatus.CANCELLED);

        String lookupReply = agentOrchestratorService.processMessage(chatId, "Cancel my Monday 2pm appointment");
        assertTrue(lookupReply.contains("Shall I cancel it"));

        String cancelReply = agentOrchestratorService.processMessage(chatId, "yes");
        assertTrue(cancelReply.contains("has been cancelled"));

        verify(googleCalendarService).cancelAppointment(monday, LocalTime.of(14, 0));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void cancellationConfirmation_acceptsTheWordCancelNotJustYes() throws Exception {
        Long chatId = 555006L;
        LocalDate monday = LocalDate.of(2026, 8, 3);
        Event existing = new Event()
                .setSummary("Appointment - Rohan")
                .setId("evt-1")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-03","time":"14:00","email":null}
                """);
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0))).thenReturn(Optional.of(existing));
        when(googleCalendarService.cancelAppointment(monday, LocalTime.of(14, 0))).thenReturn(BookingStatus.CANCELLED);

        agentOrchestratorService.processMessage(chatId, "Cancel my Monday 2pm appointment");

        // Regression test: replying "cancel" to "Shall I cancel it?" used to be read as a decline
        // because it wasn't in AFFIRMATIVE_WORDS, leaving the appointment scheduled.
        String cancelReply = agentOrchestratorService.processMessage(chatId, "cancel");
        assertTrue(cancelReply.contains("has been cancelled"));

        verify(googleCalendarService).cancelAppointment(monday, LocalTime.of(14, 0));
    }

    @Test
    void cancellationFlow_asksForDateThenLooksUpFollowUpReply() throws Exception {
        Long chatId = 555008L;
        LocalDate monday = LocalDate.of(2026, 8, 3);
        Event existing = new Event()
                .setSummary("Appointment - Rohan")
                .setId("evt-1")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));

        when(geminiService.analyzeMessage(anyString()))
                .thenReturn("""
                        {"intent":"cancel_appointment","patientName":null,"date":null,"time":null,"email":null}
                        """)
                .thenReturn("""
                        {"date":"2026-08-03","time":"14:00"}
                        """);
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0))).thenReturn(Optional.of(existing));

        // First message has no date/time, so the bot has to ask - this exercises
        // handleCancellationDetailsCollection / extractCancellationDetails on the follow-up.
        String askReply = agentOrchestratorService.processMessage(chatId, "I need to cancel my appointment");
        assertTrue(askReply.contains("what date and time"));

        String lookupReply = agentOrchestratorService.processMessage(chatId, "Monday at 2pm");
        assertTrue(lookupReply.contains("Shall I cancel it"));
    }

    @Test
    void cancellationConfirmation_negatedCancelWordStillDeclines() throws Exception {
        Long chatId = 555007L;
        LocalDate monday = LocalDate.of(2026, 8, 3);
        Event existing = new Event()
                .setSummary("Appointment - Rohan")
                .setId("evt-1")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-03","time":"14:00","email":null}
                """);
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0))).thenReturn(Optional.of(existing));

        agentOrchestratorService.processMessage(chatId, "Cancel my Monday 2pm appointment");

        // Regression test: "no, don't cancel it" contains the word "cancel", so a naive
        // keyword check would misread it as confirming the cancellation instead of declining it.
        String declineReply = agentOrchestratorService.processMessage(chatId, "no, don't cancel it");
        assertTrue(declineReply.contains("still scheduled"));

        verify(googleCalendarService, never()).cancelAppointment(any(), any());
    }

    // ---------------------------------------------------------------------
    // Greeting / unrecognized message
    // ---------------------------------------------------------------------

    @Test
    void plainGreeting_isHandledInCodeWithoutCallingGemini() {
        Long chatId = 555010L;

        String reply = agentOrchestratorService.processMessage(chatId, "Hi there");

        assertEquals("Hello! Welcome to BrightCare Clinic. How can I help you today?", reply);
        verify(geminiService, never()).analyzeMessage(anyString());
    }

    @Test
    void casuallyStretchedGreeting_isStillHandledInCodeWithoutCallingGemini() {
        Long chatId = 555024L;

        // Regression test: "hii" (real user input) used to miss the exact-match GREETING_PHRASES
        // set and fall through to Gemini instead of being answered directly in code.
        String reply = agentOrchestratorService.processMessage(chatId, "hii");

        assertEquals("Hello! Welcome to BrightCare Clinic. How can I help you today?", reply);
        verify(geminiService, never()).analyzeMessage(anyString());
    }

    @Test
    void greetingMixedWithRealContent_stillGoesThroughGeminiForTheActualIntent() throws Exception {
        Long chatId = 555022L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"15:00","email":"priya@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));

        // "hi" is present but the message carries a real request, so this must not be short-circuited
        // by the greeting fast path - it needs Gemini to extract the booking details.
        String reply = agentOrchestratorService.processMessage(chatId, "hi, book me tomorrow at 3pm, name Priya, email priya@example.com");

        assertTrue(reply.contains("available"));
        verify(geminiService).analyzeMessage(anyString());
    }

    @Test
    void llmClassifiedGreeting_stillGetsTheSameWelcomeMessage() {
        Long chatId = 555023L;

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"greeting","patientName":null,"date":null,"time":null,"email":null}
                """);

        // Not in the code-level GREETING_PHRASES list, so this exercises the Gemini-classified
        // GREETING branch rather than the fast path.
        String reply = agentOrchestratorService.processMessage(chatId, "greetings, I am new here");

        assertEquals("Hello! Welcome to BrightCare Clinic. How can I help you today?", reply);
    }

    @Test
    void unrecognizedMessage_getsRephraseFallback() {
        Long chatId = 555011L;

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"general","patientName":null,"date":null,"time":null,"email":null}
                """);

        String reply = agentOrchestratorService.processMessage(chatId, "asdkjqwe zzz random gibberish");

        assertEquals("I'm not sure I understood that. Could you rephrase?", reply);
    }

    // ---------------------------------------------------------------------
    // Booking negatives
    // ---------------------------------------------------------------------

    @Test
    void slotConfirmation_declined_startsOverWithoutBooking() throws Exception {
        Long chatId = 555012L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"15:00","email":"priya@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));

        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm, my name is Priya, email priya@example.com");

        String declineReply = agentOrchestratorService.processMessage(chatId, "no");

        assertEquals("No problem, let's start over. How can I help you today?", declineReply);
        verify(googleCalendarService, never()).createAppointment(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void booking_outsideBusinessHours_asksForDifferentTimeWithoutCheckingCalendar() throws Exception {
        Long chatId = 555013L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"20:00","email":"priya@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(20, 0))).thenReturn(false);

        String reply = agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 8pm, my name is Priya, email priya@example.com");

        assertTrue(reply.contains("outside our business hours"));
        verify(googleCalendarService, never()).checkAvailability(any(), any());
        verify(googleCalendarService, never()).createAppointment(any());
    }

    @Test
    void bookingDetailsCollection_noDateTimeExtracted_repromptsWithoutBooking() throws Exception {
        Long chatId = 555014L;

        when(geminiService.analyzeMessage(anyString()))
                .thenReturn("""
                        {"intent":"book_appointment","patientName":"Priya","date":null,"time":null,"email":"priya@example.com"}
                        """)
                .thenReturn("""
                        {"date":null,"time":null}
                        """);

        agentOrchestratorService.processMessage(chatId, "I'd like to book an appointment, name Priya, email priya@example.com");

        String reply = agentOrchestratorService.processMessage(chatId, "sometime soon-ish");

        assertTrue(reply.contains("didn't catch a specific date and time"));
        verify(googleCalendarService, never()).createAppointment(any());
    }

    @Test
    void booking_slotTakenByRaceConditionAtConfirmTime_repromptsForNewSlot() throws Exception {
        Long chatId = 555015L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"15:00","email":"priya@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));
        when(googleCalendarService.createAppointment(any())).thenReturn(BookingStatus.SLOT_TAKEN);

        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm, my name is Priya, email priya@example.com");
        agentOrchestratorService.processMessage(chatId, "yes");

        String reply = agentOrchestratorService.processMessage(chatId, "priya@example.com");

        assertTrue(reply.contains("just booked by someone else"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---------------------------------------------------------------------
    // Name / email collection negatives
    // ---------------------------------------------------------------------

    @Test
    void nameCollection_blankName_repromptsWithoutAdvancingToEmail() throws Exception {
        Long chatId = 555016L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":null,"date":"%s","time":"15:00","email":null}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));

        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm");
        agentOrchestratorService.processMessage(chatId, "yes");

        String reply = agentOrchestratorService.processMessage(chatId, "   ");

        assertEquals("Sorry, I didn't catch that. Could you tell me your name?", reply);
    }

    @Test
    void emailCollection_invalidEmail_repromptsWithoutBooking() throws Exception {
        Long chatId = 555017L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"15:00","email":null}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));

        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm, my name is Priya");
        agentOrchestratorService.processMessage(chatId, "yes");

        String reply = agentOrchestratorService.processMessage(chatId, "not-an-email");

        assertTrue(reply.contains("doesn't look like a valid email address"));
        verify(googleCalendarService, never()).createAppointment(any());
    }

    // ---------------------------------------------------------------------
    // Confirmation email delivery
    // ---------------------------------------------------------------------

    @Test
    void bookingConfirmation_emailDeliveryFails_stillConfirmsBookingButWarnsUser() throws Exception {
        Long chatId = 555018L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"book_appointment","patientName":"Priya","date":"%s","time":"15:00","email":"priya@example.com"}
                """.formatted(tomorrow));
        when(googleCalendarService.isWithinBusinessHours(tomorrow, LocalTime.of(15, 0))).thenReturn(true);
        when(googleCalendarService.checkAvailability(tomorrow, LocalTime.of(15, 0)))
                .thenReturn(new CalendarSlot(tomorrow, LocalTime.of(15, 0), LocalTime.of(15, 30), true, "Requested slot is available."));
        when(googleCalendarService.createAppointment(any())).thenReturn(BookingStatus.CONFIRMED);
        org.mockito.Mockito.doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        agentOrchestratorService.processMessage(chatId, "Book an appointment tomorrow at 3pm, my name is Priya, email priya@example.com");
        agentOrchestratorService.processMessage(chatId, "yes");

        String reply = agentOrchestratorService.processMessage(chatId, "priya@example.com");

        assertTrue(reply.contains("you're booked"));
        assertTrue(reply.contains("couldn't send the confirmation email"));
        verify(googleCalendarService).createAppointment(any());
    }

    // ---------------------------------------------------------------------
    // Cancellation negatives
    // ---------------------------------------------------------------------

    @Test
    void cancellationConfirmation_plainNo_declinesWithoutCancelling() throws Exception {
        Long chatId = 555019L;
        LocalDate monday = LocalDate.of(2026, 8, 3);
        Event existing = new Event()
                .setSummary("Appointment - Rohan")
                .setId("evt-1")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-03","time":"14:00","email":null}
                """);
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0))).thenReturn(Optional.of(existing));

        agentOrchestratorService.processMessage(chatId, "Cancel my Monday 2pm appointment");

        String reply = agentOrchestratorService.processMessage(chatId, "no");

        assertEquals("No problem, your appointment is still scheduled. Anything else?", reply);
        verify(googleCalendarService, never()).cancelAppointment(any(), any());
    }

    @Test
    void cancellation_appointmentNotFound_asksToDoubleCheck() throws Exception {
        Long chatId = 555020L;
        LocalDate monday = LocalDate.of(2026, 8, 3);

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-03","time":"14:00","email":null}
                """);
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0))).thenReturn(Optional.empty());

        String reply = agentOrchestratorService.processMessage(chatId, "Cancel my Monday 2pm appointment");

        assertTrue(reply.contains("couldn't find an appointment"));
        verify(googleCalendarService, never()).cancelAppointment(any(), any());
    }

    @Test
    void cancellationConfirmation_eventGoneByConfirmTime_reportsGoneWithoutCallingDelete() throws Exception {
        Long chatId = 555021L;
        LocalDate monday = LocalDate.of(2026, 8, 3);
        Event existing = new Event()
                .setSummary("Appointment - Rohan")
                .setId("evt-1")
                .setAttendees(List.of(new EventAttendee().setEmail("rohan@example.com")));

        when(geminiService.analyzeMessage(anyString())).thenReturn("""
                {"intent":"cancel_appointment","patientName":null,"date":"2026-08-03","time":"14:00","email":null}
                """);
        // Found during the initial lookup, but gone by the time the user confirms - e.g. cancelled
        // through another channel in between.
        when(googleCalendarService.findEvent(monday, LocalTime.of(14, 0)))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());

        agentOrchestratorService.processMessage(chatId, "Cancel my Monday 2pm appointment");

        String reply = agentOrchestratorService.processMessage(chatId, "yes");

        assertTrue(reply.contains("doesn't seem to be there anymore"));
        verify(googleCalendarService, never()).cancelAppointment(any(), any());
    }

}
