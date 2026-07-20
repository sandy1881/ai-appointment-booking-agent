package com.brightcare_clinic.appointment_agent.ai.service;

import com.brightcare_clinic.appointment_agent.ai.config.GeminiConfig;
import com.brightcare_clinic.appointment_agent.ai.dto.GeminiRequest;
import com.brightcare_clinic.appointment_agent.ai.dto.GeminiResponse;
import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    // Statuses worth retrying on the fallback model instead of failing outright: the model
    // doesn't exist on this key/region (404), it's rate-limited (429), or it's temporarily
    // overloaded (503, e.g. "This model is currently experiencing high demand").
    private static final Set<HttpStatus> FALLBACK_TRIGGER_STATUSES =
            Set.of(HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE);

    private final RestClient geminiRestClient;
    private final GeminiConfig geminiConfig;

    public String analyzeMessage(String prompt) {
        try {
            return callModel(geminiConfig.getModel(), prompt);
        } catch (HttpStatusCodeException primaryFailure) {
            String fallbackModel = geminiConfig.getFallbackModel();
            if (fallbackModel == null || fallbackModel.isBlank() || !FALLBACK_TRIGGER_STATUSES.contains(primaryFailure.getStatusCode())) {
                throw new GeminiException("Failed to call Gemini API", primaryFailure);
            }
            log.warn("Gemini model '{}' returned {}, falling back to '{}'",
                    geminiConfig.getModel(), primaryFailure.getStatusCode(), fallbackModel);
            try {
                return callModel(fallbackModel, prompt);
            } catch (RestClientException fallbackFailure) {
                throw new GeminiException("Failed to call Gemini API", fallbackFailure);
            }
        } catch (RestClientException e) {
            throw new GeminiException("Failed to call Gemini API", e);
        }
    }

    private String callModel(String model, String prompt) {
        // Note: never log the request URI here - it carries the API key as a query parameter.
        log.info("Sending request to Gemini model {}", model);
        GeminiResponse response = geminiRestClient.post()
                .uri("/models/{model}:generateContent?key={apiKey}", model, geminiConfig.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(GeminiRequest.of(prompt))
                .retrieve()
                .body(GeminiResponse.class);

        String text = response.candidates().get(0).content().parts().get(0).text();
        log.info("Received Gemini response ({} chars)", text.length());
        return text;
    }

}
