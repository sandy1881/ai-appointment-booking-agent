package com.brightcare_clinic.appointment_agent.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramConfig {

    private String username;
    private String token;

    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(token);
    }

}
