package com.brightcare_clinic.appointment_agent.ai.service;

import com.brightcare_clinic.appointment_agent.ai.config.GeminiConfig;
import com.brightcare_clinic.appointment_agent.ai.dto.GeminiRequest;
import com.brightcare_clinic.appointment_agent.ai.dto.GeminiResponse;
import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final RestClient geminiRestClient;
    private final GeminiConfig geminiConfig;

    public String analyzeMessage(String prompt) {
        // Note: never log the request URI here - it carries the API key as a query parameter.
        log.info("Sending request to Gemini model {}", geminiConfig.getModel());
        try {
            GeminiResponse response = geminiRestClient.post()
                    .uri("/models/{model}:generateContent?key={apiKey}", geminiConfig.getModel(), geminiConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(prompt))
                    .retrieve()
                    .body(GeminiResponse.class);

            String text = response.candidates().get(0).content().parts().get(0).text();
            log.info("Received Gemini response ({} chars)", text.length());
            return text;
        } catch (RestClientException e) {
            throw new GeminiException("Failed to call Gemini API", e);
        }
    }

}
