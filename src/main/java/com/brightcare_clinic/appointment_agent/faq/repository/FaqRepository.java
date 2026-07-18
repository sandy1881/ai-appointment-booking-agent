package com.brightcare_clinic.appointment_agent.faq.repository;

import com.brightcare_clinic.appointment_agent.faq.exception.FaqException;
import com.brightcare_clinic.appointment_agent.faq.model.Faq;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Repository
public class FaqRepository {

    private final ObjectMapper objectMapper;
    private List<Faq> faqs;

    public FaqRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getResourceAsStream("/faq.json")) {
            if (in == null) {
                throw new FaqException("faq.json not found on classpath", null);
            }
            faqs = objectMapper.readValue(in, new TypeReference<List<Faq>>() {
            });
        } catch (JacksonException | IOException e) {
            throw new FaqException("Failed to load FAQ data", e);
        }
    }

    public List<Faq> findAll() {
        return faqs;
    }

}
