package com.messenger.service;

import com.messenger.domain.Subscription;
import com.messenger.domain.TierType;
import com.messenger.domain.UserAccount;
import com.messenger.dto.SubscriptionStatusDto;
import com.messenger.repo.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Reads and (mock) upgrades the caller's personal subscription. The upgrade is
 * simulated locally — a production build would call a payment provider here.
 */
@Service
public class SubscriptionStatusService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionStatusService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDto status(UserAccount user) {
        Optional<Subscription> active = subscriptionRepository.findActive(user.getId(), Instant.now());
        TierType tier = active.map(Subscription::getTierType).orElse(TierType.FREE_PERSONAL);
        return new SubscriptionStatusDto(
                tier,
                tier.atLeast(TierType.PAID_FAMILY),
                active.map(Subscription::getExpiresAt).orElse(null),
                SubscriptionService.FREE_CAPSULE_MAX_DAYS
        );
    }

    /** Grant (or extend) a personal Paid Family subscription for one year. */
    @Transactional
    public SubscriptionStatusDto upgrade(UserAccount user) {
        Subscription subscription = new Subscription();
        subscription.setEntityId(user.getId());
        subscription.setTierType(TierType.PAID_FAMILY);
        subscription.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
        return status(user);
    }
}
