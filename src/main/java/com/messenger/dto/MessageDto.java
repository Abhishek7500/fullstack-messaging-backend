package com.messenger.dto;

import com.messenger.domain.ChatMessage;

import java.time.Instant;

public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String senderAvatarColor,
        String content,
        Instant createdAt
) {
    public static MessageDto from(ChatMessage message) {
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getDisplayName(),
                message.getSender().getAvatarColor(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
