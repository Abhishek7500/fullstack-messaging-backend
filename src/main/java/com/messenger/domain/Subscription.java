package com.messenger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A tier entitlement attached to an "entity".
 * <p>
 * The {@code entityId} is intentionally polymorphic: it can hold either a
 * {@code users.id} (a personal upgrade) or a {@code conversations.id} (a
 * Family/Community group upgrade). There is no FK on {@code entityId} on
 * purpose so the same table can back both; callers resolve which kind they
 * mean. A {@code null} {@code expiresAt} means "never expires".
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** users.id or conversations.id – see class docs. */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "tier_type", nullable = false, length = 24)
    private TierType tierType;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** @return true when the subscription has not yet expired at {@code now}. */
    public boolean isActive(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public TierType getTierType() {
        return tierType;
    }

    public void setTierType(TierType tierType) {
        this.tierType = tierType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
