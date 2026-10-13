package com.messenger.config;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.MessageType;
import com.messenger.domain.Subscription;
import com.messenger.domain.TierType;
import com.messenger.domain.UserAccount;
import com.messenger.dto.AuthDtos.RegisterRequest;
import com.messenger.dto.ChatEvents.CreateConversationRequest;
import com.messenger.dto.ConversationDto;
import com.messenger.repo.SubscriptionRepository;
import com.messenger.repo.UserAccountRepository;
import com.messenger.service.AuthService;
import com.messenger.service.ConversationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;
    private final ConversationService conversationService;
    private final SubscriptionRepository subscriptionRepository;

    public DemoDataInitializer(
            UserAccountRepository userAccountRepository,
            AuthService authService,
            ConversationService conversationService,
            SubscriptionRepository subscriptionRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.authService = authService;
        this.conversationService = conversationService;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public void run(String... args) {
        if (userAccountRepository.count() > 0) {
            return;
        }

        authService.register(new RegisterRequest("alice", "alice@messenger.dev", "Password123!", "Alice Johnson"));
        authService.register(new RegisterRequest("bob", "bob@messenger.dev", "Password123!", "Bob Smith"));
        authService.register(new RegisterRequest("charlie", "charlie@messenger.dev", "Password123!", "Charlie Nguyen"));
        authService.register(new RegisterRequest("dana", "dana@messenger.dev", "Password123!", "Dana Patel"));

        UserAccount alice = userAccountRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        UserAccount bob = userAccountRepository.findByUsernameIgnoreCase("bob").orElseThrow();
        UserAccount charlie = userAccountRepository.findByUsernameIgnoreCase("charlie").orElseThrow();
        UserAccount dana = userAccountRepository.findByUsernameIgnoreCase("dana").orElseThrow();

        // Alice holds a personal Paid Family tier so she can demo capsules & group media.
        Subscription aliceTier = new Subscription();
        aliceTier.setEntityId(alice.getId());
        aliceTier.setTierType(TierType.PAID_FAMILY);
        aliceTier.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
        subscriptionRepository.save(aliceTier);

        ConversationDto aliceBob = conversationService.createOrGet(alice, new CreateConversationRequest(bob.getId(), null, null));
        conversationService.saveMessage(alice, aliceBob.id(), "Hey Bob — are you free for a quick sync?");
        conversationService.saveMessage(bob, aliceBob.id(), "Yep, jumping on in 5. The realtime board looks great.");
        conversationService.saveMessage(alice, aliceBob.id(), "Awesome. I'll drop the notes in this thread.");

        // A short time-locked capsule (unlocks in ~2 minutes) from Alice.
        conversationService.saveMessage(
                alice,
                aliceBob.id(),
                "🎁 A little note for future-you: thanks for being an awesome teammate!",
                MessageType.CAPSULE,
                null,
                Instant.now().plus(2, ChronoUnit.MINUTES)
        );

        ConversationDto aliceCharlie = conversationService.createOrGet(alice, new CreateConversationRequest(charlie.getId(), null, null));
        conversationService.saveMessage(charlie, aliceCharlie.id(), "Pushed the WebSocket typing indicator. Try it out!");
        conversationService.saveMessage(alice, aliceCharlie.id(), "Nice — seeing the live dots already.");

        ConversationDto group = conversationService.createOrGet(
                alice,
                new CreateConversationRequest(null, "Product Squad", List.of(bob.getId(), charlie.getId(), dana.getId()))
        );
        conversationService.saveMessage(dana, group.id(), "Standup in 10. Bring demos if you have them.");
        conversationService.saveMessage(bob, group.id(), "I'll show the conversation list unread badges.");
    }
}

