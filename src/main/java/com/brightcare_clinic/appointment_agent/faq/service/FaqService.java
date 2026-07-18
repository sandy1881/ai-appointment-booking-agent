package com.brightcare_clinic.appointment_agent.faq.service;

import com.brightcare_clinic.appointment_agent.faq.dto.FaqResponse;
import com.brightcare_clinic.appointment_agent.faq.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqResponse findAnswer(String message) {
        String normalized = message.toLowerCase();

        FaqResponse response = faqRepository.findAll().stream()
                .filter(faq -> faq.getKeywords().stream().anyMatch(keyword -> normalized.contains(keyword.toLowerCase())))
                .findFirst()
                .map(faq -> new FaqResponse(true, faq.getAnswer()))
                .orElse(new FaqResponse(false, null));

        if (response.matched()) {
            log.info("FAQ match found for message");
        } else {
            log.info("No FAQ match found, falling back to Gemini");
        }

        return response;
    }

}
