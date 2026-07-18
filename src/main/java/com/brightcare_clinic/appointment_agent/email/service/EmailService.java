package com.brightcare_clinic.appointment_agent.email.service;

import com.brightcare_clinic.appointment_agent.email.config.MailConfig;
import com.brightcare_clinic.appointment_agent.email.dto.EmailRequest;
import com.brightcare_clinic.appointment_agent.email.exception.EmailException;
import com.brightcare_clinic.appointment_agent.email.model.EmailTemplate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateService emailTemplateService;
    private final MailConfig mailConfig;

    public void sendAppointmentConfirmation(EmailRequest request) {
        send(request.getTo(), emailTemplateService.buildAppointmentConfirmation(request));
    }

    public void sendCancellation(EmailRequest request) {
        send(request.getTo(), emailTemplateService.buildCancellation(request));
    }

    private void send(String to, EmailTemplate template) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailConfig.getFrom(), mailConfig.getFromName());
            helper.setTo(to);
            helper.setSubject(template.getSubject());
            helper.setText(template.getHtmlBody(), true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new EmailException("Failed to send email to " + to, e);
        }
    }

}
