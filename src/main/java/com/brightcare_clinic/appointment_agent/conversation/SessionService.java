package com.brightcare_clinic.appointment_agent.conversation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessions live in memory for speed, but every change is also written through to a small
 * local JSON file so in-progress conversations survive an app restart. This is a single-instance
 * local app with a handful of small session objects - a full database or Redis would be more
 * machinery than the actual problem needs; a flat file is enough to make restarts non-destructive.
 */
@Slf4j
@Service
public class SessionService {

    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public SessionService(ObjectMapper objectMapper, @Value("${session.store-path:data/sessions.json}") String storePath) {
        this.objectMapper = objectMapper;
        this.storePath = Path.of(storePath);
    }

    @PostConstruct
    void loadFromDisk() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            Map<Long, UserSession> loaded = objectMapper.readValue(storePath, new TypeReference<Map<Long, UserSession>>() {
            });
            sessions.putAll(loaded);
            log.info("Loaded {} session(s) from {}", sessions.size(), storePath);
        } catch (JacksonException e) {
            log.warn("Could not load sessions from {}, starting with none", storePath, e);
        }
    }

    public UserSession getSession(Long chatId) {
        return sessions.computeIfAbsent(chatId, UserSession::new);
    }

    public void saveSession(UserSession session) {
        sessions.put(session.getChatId(), session);
        persistToDisk();
    }

    public void removeSession(Long chatId) {
        sessions.remove(chatId);
        persistToDisk();
    }

    private void persistToDisk() {
        try {
            if (storePath.getParent() != null) {
                Files.createDirectories(storePath.getParent());
            }
            objectMapper.writeValue(storePath, sessions);
        } catch (JacksonException | IOException e) {
            log.error("Failed to persist sessions to {}", storePath, e);
        }
    }

}
