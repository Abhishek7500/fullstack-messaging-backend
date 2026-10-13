package com.messenger.dto;

import com.messenger.domain.TierType;

import java.time.Instant;

/** Snapshot of the caller's effective entitlement, shown in the UI header/modal. */
public record SubscriptionStatusDto(
        TierType tier,
        boolean premium,
        Instant expiresAt,
        long freeCapsuleMaxDays
) {
}
