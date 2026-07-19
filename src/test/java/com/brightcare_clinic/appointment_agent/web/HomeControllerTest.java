package com.brightcare_clinic.appointment_agent.web;

import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeControllerTest {

    @Test
    void home_rendersWelcomePageWithConfiguredClinicName() {
        ClinicProperties clinicProperties = new ClinicProperties();
        clinicProperties.setName("BrightCare Clinic");

        String html = new HomeController(clinicProperties).home();

        assertTrue(html.contains("Welcome to BrightCare Clinic"));
        assertTrue(html.contains("<style>"));
    }

}
