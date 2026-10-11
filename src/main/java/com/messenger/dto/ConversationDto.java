package com.messenger.dto;

import com.messenger.domain.ConversationType;

import java.time.Instant;
import java.util.List;

public record ConversationDto(
        Long id,
        ConversationType type,
        String title,
        List<UserDto> participants,
        String lastMessagePreview,
        Instant lastMessageAt,
        Long lastMessageSenderId,
        long unreadCount,
        Instant createdAt
) {
}
