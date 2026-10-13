package com.messenger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private UserAccount sender;

    /** Nullable because media-only or capsule messages may carry no text. */
    @Column(length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageType messageType = MessageType.TEXT;

    /** Storage URL / key for PHOTO and VIDEO payloads. Null for text/capsule. */
    @Column(name = "media_url", length = 1024)
    private String mediaUrl;

    /** True for time-capsule messages whose content is hidden until {@link #unlockAt}. */
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    /** When the capsule becomes readable. Null for non-capsule messages. */
    @Column(name = "unlock_at")
    private Instant unlockAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (messageType == null) {
            messageType = MessageType.TEXT;
        }
    }

    /**
     * @return true when the message is currently sealed, i.e. locked and the
     *         unlock time is still in the future relative to {@code now}.
     */
    public boolean isSealed(Instant now) {
        return locked && unlockAt != null && unlockAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public UserAccount getSender() {
        return sender;
    }

    public void setSender(UserAccount sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getUnlockAt() {
        return unlockAt;
    }

    public void setUnlockAt(Instant unlockAt) {
        this.unlockAt = unlockAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

