package com.brightcare_clinic.appointment_agent.faq.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Faq {

    private List<String> keywords;
    private String answer;

}
