package com.messenger.dto;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.MessageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire representation of a chat message.
 * <p>
 * When a message is a sealed time-capsule ({@code locked && unlockAt > now}),
 * {@link #from(ChatMessage, Instant)} strips {@code content} and {@code mediaUrl}
 * and instead exposes {@code sealed = true} plus the {@code unlockAt} target so
 * the client can render a blurred placeholder with a live countdown. Once the
 * unlock time passes, the same row is returned with its real payload.
 */
public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String senderAvatarColor,
        MessageType messageType,
        String content,
        String mediaUrl,
        boolean locked,
        boolean sealed,
        Instant unlockAt,
        List<ReactionSummary> reactions,
        Instant createdAt
) {

    /** Compact emoji tally: how many users reacted and whether I am one of them. */
    public record ReactionSummary(String reactionType, long count, boolean reactedByMe) {
    }

    public static MessageDto from(ChatMessage message) {
        return from(message, Instant.now());
    }

    public static MessageDto from(ChatMessage message, Instant now) {
        boolean sealed = message.isSealed(now);
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getDisplayName(),
                message.getSender().getAvatarColor(),
                message.getMessageType(),
                sealed ? null : message.getContent(),
                sealed ? null : message.getMediaUrl(),
                message.isLocked(),
                sealed,
                message.getUnlockAt(),
                List.of(),
                message.getCreatedAt()
        );
    }

    /** Attach reaction tallies (computed by the caller) to this DTO. */
    public MessageDto withReactions(List<ReactionSummary> summaries) {
        return new MessageDto(
                id, conversationId, senderId, senderName, senderAvatarColor,
                messageType, content, mediaUrl, locked, sealed, unlockAt,
                summaries == null ? List.of() : List.copyOf(summaries), createdAt
        );
    }

    /**
     * Aggregate a raw list of {@code (reactionType, userId)} pairs into ordered
     * tallies for a given viewer.
     */
    public static List<ReactionSummary> summarize(List<String[]> raw, Long viewerId) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, Boolean> mine = new LinkedHashMap<>();
        for (String[] row : raw) {
            String type = row[0];
            Long userId = Long.valueOf(row[1]);
            counts.computeIfAbsent(type, k -> new long[]{0})[0]++;
            if (viewerId != null && viewerId.equals(userId)) {
                mine.put(type, true);
            }
        }
        List<ReactionSummary> result = new ArrayList<>();
        counts.forEach((type, c) -> result.add(new ReactionSummary(type, c[0], mine.getOrDefault(type, false))));
        return result;
    }
}

