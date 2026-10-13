package com.messenger.service;

import com.messenger.domain.Subscription;
import com.messenger.domain.TierType;
import com.messenger.repo.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Resolves the effective subscription tier for a user and/or a conversation.
 * <p>
 * A user "has" a tier if they personally hold an active subscription at that
 * level, or if the conversation (Family/Community group) they are acting in
 * holds one. Group entitlement is intentionally generous: any active
 * {@code PAID_FAMILY} subscription on the conversation upgrades every
 * participant for that conversation.
 */
@Service
public class SubscriptionService {

    /** Free users may only seal a capsule this far into the future. */
    public static final long FREE_CAPSULE_MAX_DAYS = 30;

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * @return the strongest active tier across the user and (optionally) the
     *         conversation, defaulting to {@code FREE_PERSONAL}.
     */
    @Transactional(readOnly = true)
    public TierType effectiveTier(Long userId, Long conversationId) {
        List<Long> entityIds = new java.util.ArrayList<>();
        if (userId != null) {
            entityIds.add(userId);
        }
        if (conversationId != null) {
            entityIds.add(conversationId);
        }
        if (entityIds.isEmpty()) {
            return TierType.FREE_PERSONAL;
        }
        List<Subscription> active = subscriptionRepository.findActiveByEntityIds(entityIds, Instant.now());
        return active.stream()
                .map(Subscription::getTierType)
                .max(TierType::compareTo)
                .orElse(TierType.FREE_PERSONAL);
    }

    @Transactional(readOnly = true)
    public boolean hasTier(Long userId, Long conversationId, TierType required) {
        return effectiveTier(userId, conversationId).atLeast(required);
    }

    /**
     * Validates that {@code unlockAt} is within the range the caller's tier
     * allows. Free users may lock a capsule at most {@value #FREE_CAPSULE_MAX_DAYS}
     * days out; paid users are unlimited.
     *
     * @throws com.messenger.exception.PremiumTierRequiredException when the
     *         window exceeds the free allowance.
     */
    @Transactional(readOnly = true)
    public void assertCapsuleWindowAllowed(Long userId, Long conversationId, Instant unlockAt) {
        if (unlockAt == null) {
            return;
        }
        if (hasTier(userId, conversationId, TierType.PAID_FAMILY)) {
            return;
        }
        Instant maxFree = Instant.now().plus(FREE_CAPSULE_MAX_DAYS, ChronoUnit.DAYS);
        if (unlockAt.isAfter(maxFree)) {
            throw new com.messenger.exception.PremiumTierRequiredException(
                    "extended_capsule",
                    "Locking a capsule beyond " + FREE_CAPSULE_MAX_DAYS
                            + " days requires the Paid Family tier."
            );
        }
    }
}
