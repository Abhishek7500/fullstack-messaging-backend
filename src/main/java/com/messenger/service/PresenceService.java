package com.messenger.service;

import com.messenger.domain.UserAccount;
import com.messenger.dto.ChatEvents.PresenceEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();
    private final Map<Long, String> usernames = new ConcurrentHashMap<>();

    public PresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void userConnected(UserAccount user, String sessionId) {
        usernames.put(user.getId(), user.getUsername());
        userBySession.put(sessionId, user.getId());
        sessionsByUser.computeIfAbsent(user.getId(), id -> ConcurrentHashMap.newKeySet()).add(sessionId);
        broadcast(user.getId(), user.getUsername(), true);
    }

    public void userDisconnected(String sessionId) {
        Long userId = userBySession.remove(sessionId);
        if (userId == null) {
            return;
        }
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
                String username = usernames.getOrDefault(userId, "user-" + userId);
                broadcast(userId, username, false);
            }
        }
    }

    public boolean isOnline(Long userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<Long> onlineUserIds() {
        return sessionsByUser.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void broadcast(Long userId, String username, boolean online) {
        messagingTemplate.convertAndSend("/topic/presence", new PresenceEvent(userId, username, online));
    }
}
