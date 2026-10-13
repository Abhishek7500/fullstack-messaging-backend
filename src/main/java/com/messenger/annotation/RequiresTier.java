package com.messenger.annotation;

import com.messenger.domain.TierType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative premium-tier guard. Any Spring bean method annotated with
 * {@code @RequiresTier} is intercepted by {@code TierGuardAspect}, which
 * resolves the caller's effective tier and throws
 * {@code PremiumTierRequiredException} (HTTP 403) when it is below the required
 * level.
 *
 * <pre>{@code
 * @RequiresTier(TierType.PAID_FAMILY)
 * public LegacyArchive exportArchive(UserAccount user) { ... }
 * }</pre>
 *
 * The aspect inspects method arguments: a {@code Long} named/positioned as a
 * conversation id, or a {@code Conversation}, widens the entitlement check to
 * include the group's subscription. Otherwise the caller's personal tier is
 * used.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresTier {

    TierType value();
}
