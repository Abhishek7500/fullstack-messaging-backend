package com.messenger.dto;

import com.messenger.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChatEvents {

    private ChatEvents() {
    }

    public record CreateConversationRequest(Long userId, String title, List<Long> userIds) {
    }

    public record SendMessagePayload(
            @NotNull Long conversationId,
            @Size(max = 2000) String content,
            MessageType messageType,
            String mediaUrl,
            Instant unlockAt
    ) {
        public SendMessagePayload(Long conversationId, String content) {
            this(conversationId, content, MessageType.TEXT, null, null);
        }
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

    /** Toggle a reaction; the server flips it on/off based on current state. */
    public record ReactionRequest(
            @NotNull Long messageId,
            @NotBlank @Size(max = 16) String reactionType
    ) {
    }

    /** REST body for {@code POST /api/messages/{id}/reactions} (id comes from the path). */
    public record ReactionToggleRequest(
            @NotBlank @Size(max = 16) String reactionType
    ) {
    }

    /** Broadcast tally update for a single message. */
    public record ReactionEvent(
            Long messageId,
            Long conversationId,
            List<MessageDto.ReactionSummary> reactions,
            Instant updatedAt
    ) {
    }

    /** Result of a mock media upload. */
    public record MediaUploadResponse(
            String url,
            String contentType,
            long sizeBytes
    ) {
    }
}
