package com.brightcare_clinic.appointment_agent.telegram;

import com.brightcare_clinic.appointment_agent.agent.AgentOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramClient telegramClient;
    private final AgentOrchestratorService agentOrchestratorService;

    public void handleUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            log.info("Received message from chatId {}: {}", chatId, text);
            String response = agentOrchestratorService.processMessage(chatId, text);
            reply(chatId, response);
        }
    }

    private void reply(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send reply to chatId {}", chatId, e);
        }
    }

}
