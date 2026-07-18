package com.brightcare_clinic.appointment_agent.conversation;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession getSession(Long chatId) {
        return sessions.computeIfAbsent(chatId, UserSession::new);
    }

    public void saveSession(UserSession session) {
        sessions.put(session.getChatId(), session);
    }

    public void removeSession(Long chatId) {
        sessions.remove(chatId);
    }

}
