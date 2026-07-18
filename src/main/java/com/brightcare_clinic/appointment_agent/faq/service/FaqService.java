package com.brightcare_clinic.appointment_agent.faq.service;

import com.brightcare_clinic.appointment_agent.faq.dto.FaqResponse;
import com.brightcare_clinic.appointment_agent.faq.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqResponse findAnswer(String message) {
        String normalized = message.toLowerCase();

        return faqRepository.findAll().stream()
                .filter(faq -> faq.getKeywords().stream().anyMatch(keyword -> normalized.contains(keyword.toLowerCase())))
                .findFirst()
                .map(faq -> new FaqResponse(true, faq.getAnswer()))
                .orElse(new FaqResponse(false, null));
    }

}
