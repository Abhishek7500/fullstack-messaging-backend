package com.messenger.repo;

import com.messenger.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Highest-tier active subscription across a set of entities (e.g. the
     * viewer's user id plus the conversation id). Ordering lets the caller
     * pick the strongest entitlement with {@code findFirst()}.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.entityId IN :entityIds
              AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            ORDER BY s.tierType DESC
            """)
    List<Subscription> findActiveByEntityIds(@Param("entityIds") List<Long> entityIds, @Param("now") Instant now);

    /** Active subscriptions for a single entity, strongest tier first. */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.entityId = :entityId
              AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            ORDER BY s.tierType DESC
            """)
    List<Subscription> findActiveByEntityId(@Param("entityId") Long entityId, @Param("now") Instant now);

    default Optional<Subscription> findActive(Long entityId, Instant now) {
        return findActiveByEntityId(entityId, now).stream().findFirst();
    }
}

