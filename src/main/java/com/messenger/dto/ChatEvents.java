package com.messenger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChatEvents {

    private ChatEvents() {
    }

    public record CreateConversationRequest(Long userId, String title, List<Long> userIds) {
    }

    public record SendMessagePayload(
            @NotNull Long conversationId,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record TypingPayload(
            @NotNull Long conversationId,
            boolean typing
    ) {
    }

    public record TypingEvent(
            Long conversationId,
            Long userId,
            String displayName,
            boolean typing
    ) {
    }

    public record PresenceEvent(
            Long userId,
            String username,
            boolean online
    ) {
    }
}
