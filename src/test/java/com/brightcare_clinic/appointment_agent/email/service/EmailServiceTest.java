package com.brightcare_clinic.appointment_agent.email.service;

import com.brightcare_clinic.appointment_agent.email.config.MailConfig;
import com.brightcare_clinic.appointment_agent.email.dto.EmailRequest;
import com.brightcare_clinic.appointment_agent.email.exception.EmailException;
import com.brightcare_clinic.appointment_agent.email.model.EmailTemplate;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MailConfig mailConfig = new MailConfig();
        mailConfig.setFrom("clinic@example.com");
        mailConfig.setFromName("BrightCare Clinic");

        emailService = new EmailService(mailSender, emailTemplateService, mailConfig);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendAppointmentConfirmation_sendsRenderedTemplateViaMailSender() {
        EmailRequest request = new EmailRequest("patient@example.com", "Rohan", LocalDate.of(2026, 8, 1), LocalTime.of(15, 0));
        when(emailTemplateService.buildAppointmentConfirmation(request))
                .thenReturn(new EmailTemplate("Appointment Confirmed - BrightCare Clinic", "<html>body</html>"));

        emailService.sendAppointmentConfirmation(request);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendCancellation_sendsRenderedTemplateViaMailSender() {
        EmailRequest request = new EmailRequest("patient@example.com", "Rohan", LocalDate.of(2026, 8, 1), LocalTime.of(15, 0));
        when(emailTemplateService.buildCancellation(request))
                .thenReturn(new EmailTemplate("Appointment Cancelled - BrightCare Clinic", "<html>cancelled</html>"));

        emailService.sendCancellation(request);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendAppointmentConfirmation_whenMailSenderFails_throwsEmailException() {
        EmailRequest request = new EmailRequest("patient@example.com", "Rohan", LocalDate.of(2026, 8, 1), LocalTime.of(15, 0));
        when(emailTemplateService.buildAppointmentConfirmation(request))
                .thenReturn(new EmailTemplate("Subject", "<html>body</html>"));
        doThrow(new MailSendException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(EmailException.class, () -> emailService.sendAppointmentConfirmation(request));
    }

}
