package com.messenger.aop;

import com.messenger.annotation.RequiresTier;
import com.messenger.domain.Conversation;
import com.messenger.domain.TierType;
import com.messenger.domain.UserAccount;
import com.messenger.exception.PremiumTierRequiredException;
import com.messenger.security.SecurityUtils;
import com.messenger.service.SubscriptionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Enforces {@link RequiresTier}. Resolves the caller via
 * {@link SecurityUtils#currentUser()} and inspects the annotated method's
 * arguments for a conversation context (a {@link Conversation} or a
 * {@code Long} argument named {@code conversationId}) so group-level
 * subscriptions are honoured.
 */
@Aspect
@Order(0)
@Component
public class TierGuardAspect {

    private final SubscriptionService subscriptionService;

    public TierGuardAspect(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Around("@annotation(requiresTier)")
    public Object guard(ProceedingJoinPoint joinPoint, RequiresTier requiresTier) throws Throwable {
        UserAccount user = SecurityUtils.currentUser();
        Long conversationId = extractConversationId(joinPoint);
        TierType required = requiresTier.value();

        if (!subscriptionService.hasTier(user.getId(), conversationId, required)) {
            throw new PremiumTierRequiredException(
                    "tier_" + required.name().toLowerCase(),
                    "This action requires the " + required.name() + " tier."
            );
        }
        return joinPoint.proceed();
    }

    /** Best-effort extraction of a conversation id from the method arguments. */
    private Long extractConversationId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (names == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Conversation conversation) {
                return conversation.getId();
            }
            if (arg instanceof Long && Objects.equals(names[i], "conversationId")) {
                return (Long) arg;
            }
        }
        return null;
    }
}
