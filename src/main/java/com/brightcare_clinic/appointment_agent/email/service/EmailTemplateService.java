package com.brightcare_clinic.appointment_agent.email.service;

import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import com.brightcare_clinic.appointment_agent.email.dto.EmailRequest;
import com.brightcare_clinic.appointment_agent.email.model.EmailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private static final String ACCENT_COLOR = "#0F9D8C";
    private static final String DANGER_COLOR = "#D64545";
    private static final String TEXT_COLOR = "#1F2933";
    private static final String MUTED_COLOR = "#6B7280";
    private static final String BORDER_COLOR = "#E5E7EB";

    private final ClinicProperties clinicProperties;

    public EmailTemplate buildAppointmentConfirmation(EmailRequest request) {
        String subject = "Appointment Confirmed - " + clinicProperties.getName();
        String html = renderEmail(
                ACCENT_COLOR,
                "✓",
                "Appointment Confirmed",
                "Your appointment has been successfully booked. We look forward to seeing you.",
                request);

        return new EmailTemplate(subject, html);
    }

    public EmailTemplate buildCancellation(EmailRequest request) {
        String subject = "Appointment Cancelled - " + clinicProperties.getName();
        String html = renderEmail(
                DANGER_COLOR,
                "✕",
                "Appointment Cancelled",
                "Your appointment has been cancelled. If this wasn't expected, please contact us.",
                request);

        return new EmailTemplate(subject, html);
    }

    private String renderEmail(String accentColor, String badgeIcon, String heading, String message, EmailRequest request) {
        String patientName = request.getPatientName() != null ? request.getPatientName() : "Patient";
        String formattedDate = request.getDate().format(DATE_FORMATTER);
        String formattedTime = request.getTime().format(TIME_FORMATTER);

        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0; padding:0; background-color:#F3F4F6; font-family:'Segoe UI', Helvetica, Arial, sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#F3F4F6; padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px; background-color:#FFFFFF; border-radius:12px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                          <tr>
                            <td style="background-color:%s; padding:28px 32px;">
                              <table role="presentation" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="width:36px; height:36px; background-color:rgba(255,255,255,0.2); border-radius:50%%; text-align:center; vertical-align:middle; font-size:18px; color:#FFFFFF; font-weight:bold;">%s</td>
                                  <td style="padding-left:12px; font-size:18px; font-weight:600; color:#FFFFFF;">%s</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              <h1 style="margin:0 0 8px 0; font-size:22px; color:%s;">%s</h1>
                              <p style="margin:0 0 24px 0; font-size:14px; line-height:1.6; color:%s;">%s</p>
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid %s; border-radius:8px; overflow:hidden;">
                                <tr>
                                  <td style="padding:14px 16px; font-size:13px; color:%s; border-bottom:1px solid %s; width:35%%;">Patient</td>
                                  <td style="padding:14px 16px; font-size:14px; color:%s; font-weight:600; border-bottom:1px solid %s;">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:14px 16px; font-size:13px; color:%s; border-bottom:1px solid %s;">Date</td>
                                  <td style="padding:14px 16px; font-size:14px; color:%s; font-weight:600; border-bottom:1px solid %s;">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:14px 16px; font-size:13px; color:%s;">Time</td>
                                  <td style="padding:14px 16px; font-size:14px; color:%s; font-weight:600;">%s</td>
                                </tr>
                              </table>
                              <p style="margin:24px 0 0 0; font-size:13px; line-height:1.6; color:%s;">Thank you for choosing %s.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:16px 32px; background-color:#FAFAFA; border-top:1px solid %s;">
                              <p style="margin:0; font-size:12px; color:%s;">This is an automated message from %s. Please do not reply directly to this email.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                accentColor, badgeIcon, clinicProperties.getName(),
                accentColor, heading,
                TEXT_COLOR, message,
                BORDER_COLOR,
                MUTED_COLOR, BORDER_COLOR, TEXT_COLOR, BORDER_COLOR, patientName,
                MUTED_COLOR, BORDER_COLOR, TEXT_COLOR, BORDER_COLOR, formattedDate,
                MUTED_COLOR, TEXT_COLOR, formattedTime,
                MUTED_COLOR, clinicProperties.getName(),
                BORDER_COLOR,
                MUTED_COLOR, clinicProperties.getName());
    }

}
