package com.messenger.domain;

/**
 * Kind of payload carried by a {@link ChatMessage}.
 * <ul>
 *   <li>{@code TEXT}    – plain text (default, backward compatible)</li>
 *   <li>{@code PHOTO}   – an image referenced by {@code mediaUrl}</li>
 *   <li>{@code VIDEO}   – a video referenced by {@code mediaUrl}</li>
 *   <li>{@code CAPSULE} – a time-locked message revealed at {@code unlockAt}</li>
 * </ul>
 */
public enum MessageType {
    TEXT,
    PHOTO,
    VIDEO,
    CAPSULE
}
