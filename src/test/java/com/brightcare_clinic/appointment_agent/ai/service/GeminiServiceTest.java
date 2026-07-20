package com.brightcare_clinic.appointment_agent.ai.service;

import com.brightcare_clinic.appointment_agent.ai.config.GeminiConfig;
import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiServiceTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private static final String SAMPLE_RESPONSE = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"intent\\":\\"greeting\\"}"}]}}]}
            """;

    private MockRestServiceServer mockServer;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiConfig geminiConfig = new GeminiConfig();
        geminiConfig.setApiKey("test-key");
        geminiConfig.setModel("gemini-3.5-flash");
        geminiConfig.setFallbackModel("gemini-2.5-flash");

        geminiService = new GeminiService(restClient, geminiConfig);
    }

    @Test
    void analyzeMessage_usesConfiguredPrimaryModel_whenAvailable() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

        String result = geminiService.analyzeMessage("prompt");

        assertEquals("{\"intent\":\"greeting\"}", result);
        mockServer.verify();
    }

    @Test
    void analyzeMessage_fallsBackToSecondaryModel_whenPrimaryModelNotFound() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

        String result = geminiService.analyzeMessage("prompt");

        assertEquals("{\"intent\":\"greeting\"}", result);
        mockServer.verify();
    }

    @Test
    void analyzeMessage_fallsBackToSecondaryModel_whenPrimaryModelOverloaded() {
        // Matches the real-world case: the model exists but returns 503 "currently experiencing
        // high demand" - this should still trigger the fallback, not just a 404.
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

        String result = geminiService.analyzeMessage("prompt");

        assertEquals("{\"intent\":\"greeting\"}", result);
        mockServer.verify();
    }

    @Test
    void analyzeMessage_fallsBackToSecondaryModel_whenPrimaryRateLimited() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

        String result = geminiService.analyzeMessage("prompt");

        assertEquals("{\"intent\":\"greeting\"}", result);
        mockServer.verify();
    }

    @Test
    void analyzeMessage_doesNotFallBack_onNonRetryableServerError() {
        // A generic 500 isn't in the retryable set - falling back would just mask real bugs
        // (bad request shape, etc.) as if they were model-availability issues.
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(GeminiException.class, () -> geminiService.analyzeMessage("prompt"));
        mockServer.verify();
    }

    @Test
    void analyzeMessage_throwsGeminiException_whenBothPrimaryAndFallbackModelsFail() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.5-flash:generateContent?key=test-key"))
                .andRespond(withServerError());

        assertThrows(GeminiException.class, () -> geminiService.analyzeMessage("prompt"));
    }

    @Test
    void analyzeMessage_doesNotFallBack_whenNoFallbackModelConfigured() {
        GeminiConfig geminiConfig = new GeminiConfig();
        geminiConfig.setApiKey("test-key");
        geminiConfig.setModel("gemini-3.5-flash");
        geminiConfig.setFallbackModel(null);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        geminiService = new GeminiService(builder.build(), geminiConfig);

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-3.5-flash:generateContent?key=test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(GeminiException.class, () -> geminiService.analyzeMessage("prompt"));
        mockServer.verify();
    }

}
