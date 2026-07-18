package com.brightcare_clinic.appointment_agent.faq.service;

import com.brightcare_clinic.appointment_agent.faq.dto.FaqResponse;
import com.brightcare_clinic.appointment_agent.faq.model.Faq;
import com.brightcare_clinic.appointment_agent.faq.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqRepository faqRepository;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqRepository);

        Faq timings = new Faq();
        timings.setKeywords(List.of("timing", "timings", "hours"));
        timings.setAnswer("We are open Monday to Saturday, 9:00 AM to 6:00 PM.");

        Faq address = new Faq();
        address.setKeywords(List.of("address", "location", "where is your clinic"));
        address.setAnswer("We are located at 123 MG Road, Bengaluru.");

        when(faqRepository.findAll()).thenReturn(List.of(timings, address));
    }

    @Test
    void findAnswer_whenKeywordMatches_returnsMatchedResponse() {
        FaqResponse response = faqService.findAnswer("What are your clinic timings?");

        assertTrue(response.matched());
        assertEquals("We are open Monday to Saturday, 9:00 AM to 6:00 PM.", response.answer());
    }

    @Test
    void findAnswer_whenMultiWordKeywordMatches_returnsMatchedResponse() {
        FaqResponse response = faqService.findAnswer("Where is your clinic?");

        assertTrue(response.matched());
        assertEquals("We are located at 123 MG Road, Bengaluru.", response.answer());
    }

    @Test
    void findAnswer_whenNoKeywordMatches_fallsBackToGemini() {
        FaqResponse response = faqService.findAnswer("Do you accept insurance?");

        assertFalse(response.matched());
        assertNull(response.answer());
    }

    @Test
    void findAnswer_isCaseInsensitive() {
        FaqResponse response = faqService.findAnswer("WHAT ARE YOUR TIMINGS");

        assertTrue(response.matched());
    }

}
