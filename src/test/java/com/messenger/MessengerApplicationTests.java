package com.messenger;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.MessageType;
import com.messenger.domain.Subscription;
import com.messenger.domain.TierType;
import com.messenger.domain.UserAccount;
import com.messenger.dto.AuthDtos.RegisterRequest;
import com.messenger.dto.ChatEvents.CreateConversationRequest;
import com.messenger.dto.MessageDto;
import com.messenger.exception.PremiumTierRequiredException;
import com.messenger.repo.SubscriptionRepository;
import com.messenger.repo.UserAccountRepository;
import com.messenger.service.AuthService;
import com.messenger.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration coverage for the TimeCapsule premium gates and time-lock redaction.
 * Runs against the in-memory H2 datasource seeded by {@code DemoDataInitializer}.
 */
@SpringBootTest
class MessengerApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ConversationService conversationService;

    @Test
    void contextLoads() {
    }

    private UserAccount freshUser(String name) {
        authService.register(new RegisterRequest(name, name + "@test.dev", "Password123!", name));
        return userAccountRepository.findByUsernameIgnoreCase(name).orElseThrow();
    }

    @Test
    void freeUserCannotLockCapsuleBeyondFreeWindow() {
        UserAccount free = freshUser("freeuser");
        UserAccount other = freshUser("freeother");
        var convo = conversationService.createOrGet(free, new CreateConversationRequest(other.getId(), null, null));

        Instant tooFar = Instant.now().plus(45, ChronoUnit.DAYS);
        PremiumTierRequiredException ex = assertThrows(
                PremiumTierRequiredException.class,
                () -> conversationService.saveMessage(free, convo.id(), "secret", MessageType.CAPSULE, null, tooFar)
        );
        assertEquals("extended_capsule", ex.getFeature());

        // Within the 30-day free window it must succeed.
        ChatMessage ok = conversationService.saveMessage(
                free, convo.id(), "near secret", MessageType.CAPSULE, null,
                Instant.now().plus(10, ChronoUnit.DAYS));
        assertTrue(ok.isLocked());
    }

    @Test
    void freeUserCannotShareMediaInGroupButCanInDirect() {
        UserAccount free = freshUser("mediafree");
        UserAccount other = freshUser("mediaother");
        UserAccount other2 = freshUser("mediaother2");
        var dm = conversationService.createOrGet(free, new CreateConversationRequest(other.getId(), null, null));
        // Direct chat: media is always allowed.
        assertDoesNotThrow(() -> conversationService.saveMessage(
                free, dm.id(), null, MessageType.PHOTO, "http://img/1", null));

        // Group chat (needs 2+ other members): media requires PAID_FAMILY.
        var group = conversationService.createOrGet(
                free, new CreateConversationRequest(null, "Free Group", List.of(other.getId(), other2.getId())));
        assertThrows(PremiumTierRequiredException.class, () -> conversationService.saveMessage(
                free, group.id(), null, MessageType.PHOTO, "http://img/2", null));
    }

    @Test
    void paidGroupUpgradeAllowsMediaForMembers() {
        UserAccount owner = freshUser("groupowner");
        UserAccount member = freshUser("groupmember");
        UserAccount member2 = freshUser("groupmember2");
        var group = conversationService.createOrGet(
                owner, new CreateConversationRequest(null, "Paid Group", List.of(member.getId(), member2.getId())));

        Subscription sub = new Subscription();
        sub.setEntityId(group.id());
        sub.setTierType(TierType.PAID_FAMILY);
        subscriptionRepository.save(sub);

        // Member now inherits the group's PAID_FAMILY entitlement for this conversation.
        assertDoesNotThrow(() -> conversationService.saveMessage(
                member, group.id(), null, MessageType.VIDEO, "http://vid/1", null));
    }

    @Test
    void sealedCapsuleIsRedactedInHistory() {
        UserAccount user = freshUser("capsuleuser");
        UserAccount other = freshUser("capsuleother");
        var convo = conversationService.createOrGet(user, new CreateConversationRequest(other.getId(), null, null));
        conversationService.saveMessage(
                user, convo.id(), "future note", MessageType.CAPSULE, null,
                Instant.now().plus(5, ChronoUnit.DAYS));

        var page = conversationService.history(user, convo.id(), 0, 10);
        MessageDto dto = page.getContent().stream()
                .filter(m -> m.messageType() == MessageType.CAPSULE)
                .findFirst().orElseThrow();

        assertTrue(dto.sealed(), "capsule should be sealed");
        assertTrue(dto.locked());
        assertNull(dto.content(), "sealed content must be hidden");
        assertNotNull(dto.unlockAt(), "countdown target must be present");
    }
}

