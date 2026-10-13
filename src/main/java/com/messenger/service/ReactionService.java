package com.messenger.service;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.Conversation;
import com.messenger.domain.Reaction;
import com.messenger.domain.UserAccount;
import com.messenger.dto.ChatEvents.ReactionEvent;
import com.messenger.dto.MessageDto;
import com.messenger.repo.ChatMessageRepository;
import com.messenger.repo.ReactionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Toggles reactions on a message and fans the updated tally out to every
 * participant of the conversation over the STOMP broker.
 */
@Service
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ReactionService(
            ReactionRepository reactionRepository,
            ChatMessageRepository chatMessageRepository,
            ConversationService conversationService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.reactionRepository = reactionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Add the reaction if absent, otherwise remove it, then broadcast the fresh
     * tallies to {@code /topic/conversations/{id}/reactions}.
     *
     * @return the message id that changed
     */
    @Transactional
    public Long toggle(UserAccount user, Long messageId, String reactionType) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        Long conversationId = message.getConversation().getId();
        conversationService.requireParticipant(conversationId, user.getId());

        reactionRepository
                .findByMessageIdAndUserIdAndReactionType(messageId, user.getId(), reactionType)
                .ifPresentOrElse(
                        existing -> reactionRepository.delete(existing),
                        () -> {
                            Reaction reaction = new Reaction();
                            reaction.setMessage(message);
                            reaction.setUser(user);
                            reaction.setReactionType(reactionType);
                            reactionRepository.save(reaction);
                        }
                );

        publishTallies(message, user.getId());
        return messageId;
    }

    /** Recompute and broadcast the reaction tallies for a single message. */
    public void publishTallies(ChatMessage message, Long viewerId) {
        List<Object[]> raw = reactionRepository.findRawByMessageIds(List.of(message.getId()));
        List<String[]> rows = new ArrayList<>();
        for (Object[] row : raw) {
            rows.add(new String[]{String.valueOf(row[0]), String.valueOf(row[1])});
        }
        List<MessageDto.ReactionSummary> summaries = MessageDto.summarize(rows, viewerId);
        Long conversationId = message.getConversation().getId();
        ReactionEvent event = new ReactionEvent(
                message.getId(),
                conversationId,
                summaries,
                Instant.now()
        );
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/reactions", event);
    }
}
