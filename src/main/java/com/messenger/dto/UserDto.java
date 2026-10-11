package com.messenger.dto;

import com.messenger.domain.UserAccount;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarColor,
        Instant createdAt,
        boolean online
) {
    public static UserDto from(UserAccount user, boolean online) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarColor(),
                user.getCreatedAt(),
                online
        );
    }
}
