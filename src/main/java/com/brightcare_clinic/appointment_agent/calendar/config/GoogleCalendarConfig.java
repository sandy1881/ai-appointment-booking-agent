package com.brightcare_clinic.appointment_agent.calendar.config;

import com.brightcare_clinic.appointment_agent.calendar.exception.CalendarException;
import com.brightcare_clinic.appointment_agent.calendar.model.CalendarConstants;
import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

@Configuration
@RequiredArgsConstructor
public class GoogleCalendarConfig {

    private final ClinicProperties clinicProperties;

    @Value("${google.credentials-path}")
    private String credentialsPath;

    @Bean
    @Lazy
    public Calendar calendarClient() {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            GoogleAuthorizationCodeFlow flow = buildFlow(httpTransport, jsonFactory);

            Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().build())
                    .authorize("user");

            return new Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName(clinicProperties.getName())
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new CalendarException("Failed to authenticate with Google Calendar", e);
        }
    }

    private GoogleAuthorizationCodeFlow buildFlow(HttpTransport httpTransport, JsonFactory jsonFactory) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(credentialsPath))) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));

            return new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, CalendarConstants.SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(CalendarConstants.TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();
        }
    }

}
