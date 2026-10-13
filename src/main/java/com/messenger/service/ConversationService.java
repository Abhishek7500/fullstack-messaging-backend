package com.messenger.service;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.Conversation;
import com.messenger.domain.ConversationParticipant;
import com.messenger.domain.ConversationType;
import com.messenger.domain.MessageType;
import com.messenger.domain.UserAccount;
import com.messenger.dto.ChatEvents.CreateConversationRequest;
import com.messenger.dto.ConversationDto;
import com.messenger.dto.MessageDto;
import com.messenger.dto.UserDto;
import com.messenger.exception.PremiumTierRequiredException;
import com.messenger.repo.ChatMessageRepository;
import com.messenger.repo.ConversationParticipantRepository;
import com.messenger.repo.ConversationRepository;
import com.messenger.repo.ReactionRepository;
import com.messenger.repo.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserAccountRepository userAccountRepository;
    private final PresenceService presenceService;
    private final SubscriptionService subscriptionService;
    private final ReactionRepository reactionRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            ChatMessageRepository chatMessageRepository,
            UserAccountRepository userAccountRepository,
            PresenceService presenceService,
            SubscriptionService subscriptionService,
            ReactionRepository reactionRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userAccountRepository = userAccountRepository;
        this.presenceService = presenceService;
        this.subscriptionService = subscriptionService;
        this.reactionRepository = reactionRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listFor(UserAccount current) {
        List<ConversationParticipant> memberships = participantRepository.findInboxForUser(current.getId());
        List<ConversationDto> result = new ArrayList<>();
        for (ConversationParticipant membership : memberships) {
            result.add(toDto(membership.getConversation(), current));
        }
        result.sort(Comparator.comparing(
                dto -> dto.lastMessageAt() != null ? dto.lastMessageAt() : dto.createdAt(),
                Comparator.reverseOrder()
        ));
        return result;
    }

    @Transactional(readOnly = true)
    public ConversationDto getFor(UserAccount current, Long conversationId) {
        requireParticipant(conversationId, current.getId());
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        return toDto(conversation, current);
    }

    @Transactional
    public ConversationDto createOrGet(UserAccount current, CreateConversationRequest request) {
        if (request.userId() != null) {
            return getOrCreateDirect(current, request.userId());
        }
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            if (request.userIds().size() == 1) {
                return getOrCreateDirect(current, request.userIds().get(0));
            }
            return createGroup(current, request.title(), request.userIds());
        }
        throw new IllegalArgumentException("Provide userId or userIds to start a conversation");
    }

    @Transactional
    public ConversationDto markRead(UserAccount current, Long conversationId) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, current.getId())
                .orElseThrow(() -> new SecurityException("You are not a participant of this conversation"));
        participant.setLastReadAt(Instant.now());
        participantRepository.save(participant);
        return toDto(participant.getConversation(), current);
    }

    @Transactional
    public ChatMessage saveMessage(UserAccount sender, Long conversationId, String content) {
        return saveMessage(sender, conversationId, content, MessageType.TEXT, null, null);
    }

    /**
     * Persist a message of any {@link MessageType}, enforcing premium rules:
     * <ul>
     *   <li>PHOTO/VIDEO inside a FREE group chat → {@link PremiumTierRequiredException}</li>
     *   <li>CAPSULE with an {@code unlockAt} beyond the free window → {@link PremiumTierRequiredException}</li>
     * </ul>
     */
    @Transactional
    public ChatMessage saveMessage(
            UserAccount sender,
            Long conversationId,
            String content,
            MessageType messageType,
            String mediaUrl,
            Instant unlockAt
    ) {
        requireParticipant(conversationId, sender.getId());
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        MessageType resolvedType = messageType == null ? MessageType.TEXT : messageType;
        boolean capsule = resolvedType == MessageType.CAPSULE;
        boolean media = resolvedType == MessageType.PHOTO || resolvedType == MessageType.VIDEO;

        if (media && conversation.getType() == ConversationType.GROUP) {
            boolean paid = subscriptionService.hasTier(sender.getId(), conversationId, com.messenger.domain.TierType.PAID_FAMILY);
            if (!paid) {
                throw new PremiumTierRequiredException(
                        "group_media",
                        "Sharing photos and videos in a group chat requires the Paid Family tier."
                );
            }
        }

        if (capsule && unlockAt != null) {
            subscriptionService.assertCapsuleWindowAllowed(sender.getId(), conversationId, unlockAt);
            if (!unlockAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("A capsule must unlock in the future");
            }
        }

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content == null ? null : content.trim());
        message.setMessageType(resolvedType);
        message.setMediaUrl(mediaUrl);
        message.setLocked(capsule);
        message.setUnlockAt(capsule ? unlockAt : null);
        ChatMessage saved = chatMessageRepository.save(message);

        conversation.setLastMessagePreview(previewFor(saved));
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversation.setLastMessageSenderId(sender.getId());
        conversationRepository.save(conversation);

        ConversationParticipant senderMembership = participantRepository
                .findByConversationIdAndUserId(conversationId, sender.getId())
                .orElseThrow();
        senderMembership.setLastReadAt(saved.getCreatedAt());
        participantRepository.save(senderMembership);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<MessageDto> history(UserAccount current, Long conversationId, int page, int size) {
        requireParticipant(conversationId, current.getId());
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<ChatMessage> messages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(safePage, safeSize));

        Instant now = Instant.now();
        List<Long> ids = messages.getContent().stream().map(ChatMessage::getId).toList();
        Map<Long, List<MessageDto.ReactionSummary>> reactionMap = reactionTallies(ids, current.getId());

        return messages.map(message -> MessageDto
                .from(message, now)
                .withReactions(reactionMap.getOrDefault(message.getId(), List.of())));
    }

    /** Build per-message reaction tallies for the given viewer. */
    private Map<Long, List<MessageDto.ReactionSummary>> reactionTallies(List<Long> messageIds, Long viewerId) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        // findRawByMessageIds returns rows of {reactionType, userId, messageId}
        Map<Long, List<String[]>> byMessage = new java.util.HashMap<>();
        for (Object[] row : reactionRepository.findRawByMessageIds(messageIds)) {
            String reactionType = String.valueOf(row[0]);
            String userId = String.valueOf(row[1]);
            Long messageId = (Long) row[2];
            byMessage.computeIfAbsent(messageId, k -> new ArrayList<>()).add(new String[]{reactionType, userId});
        }
        Map<Long, List<MessageDto.ReactionSummary>> result = new java.util.HashMap<>();
        byMessage.forEach((messageId, rows) ->
                result.put(messageId, MessageDto.summarize(rows, viewerId)));
        return result;
    }

    public void requireParticipant(Long conversationId, Long userId) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new SecurityException("You are not a participant of this conversation");
        }
    }

    public List<UserAccount> participants(Long conversationId) {
        return participantRepository.findByConversationId(conversationId).stream()
                .map(ConversationParticipant::getUser)
                .toList();
    }

    private ConversationDto getOrCreateDirect(UserAccount current, Long otherUserId) {
        if (current.getId().equals(otherUserId)) {
            throw new IllegalArgumentException("Cannot start a conversation with yourself");
        }
        UserAccount other = userAccountRepository.findById(otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String pairKey = pairKey(current.getId(), other.getId());
        Conversation conversation = conversationRepository.findByPairKey(pairKey).orElseGet(() -> {
            Conversation created = new Conversation();
            created.setType(ConversationType.DIRECT);
            created.setPairKey(pairKey);
            Conversation saved = conversationRepository.save(created);
            addParticipant(saved, current);
            addParticipant(saved, other);
            return saved;
        });
        return toDto(conversation, current);
    }

    private ConversationDto createGroup(UserAccount current, String title, List<Long> userIds) {
        Set<Long> unique = new HashSet<>(userIds);
        unique.remove(current.getId());
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("A group needs at least one other participant");
        }
        Conversation created = new Conversation();
        created.setType(ConversationType.GROUP);
        created.setTitle((title == null || title.isBlank()) ? "Group chat" : title.trim());
        Conversation saved = conversationRepository.save(created);
        addParticipant(saved, current);
        for (Long userId : unique) {
            UserAccount member = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            addParticipant(saved, member);
        }
        return toDto(saved, current);
    }

    private void addParticipant(Conversation conversation, UserAccount user) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setLastReadAt(Instant.now());
        participantRepository.save(participant);
    }

    private ConversationDto toDto(Conversation conversation, UserAccount current) {
        List<UserDto> members = participantRepository.findByConversationId(conversation.getId()).stream()
                .map(ConversationParticipant::getUser)
                .map(user -> UserDto.from(user, presenceService.isOnline(user.getId())))
                .toList();
        ConversationParticipant self = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), current.getId())
                .orElseThrow(() -> new SecurityException("You are not a participant of this conversation"));
        Instant lastRead = self.getLastReadAt() == null ? Instant.EPOCH : self.getLastReadAt();
        long unread = chatMessageRepository.countUnread(conversation.getId(), current.getId(), lastRead);
        String title = conversation.getTitle();
        if (conversation.getType() == ConversationType.DIRECT) {
            title = members.stream()
                    .filter(user -> !user.id().equals(current.getId()))
                    .map(UserDto::displayName)
                    .findFirst()
                    .orElse("Direct message");
        }
        return new ConversationDto(
                conversation.getId(),
                conversation.getType(),
                title,
                members,
                conversation.getLastMessagePreview(),
                conversation.getLastMessageAt(),
                conversation.getLastMessageSenderId(),
                unread,
                conversation.getCreatedAt()
        );
    }

    public static String pairKey(Long a, Long b) {
        long first = Math.min(a, b);
        long second = Math.max(a, b);
        return first + ":" + second;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    /** Human-readable inbox preview that respects capsules and media. */
    private String previewFor(ChatMessage message) {
        if (message.getMessageType() == MessageType.CAPSULE || message.isLocked()) {
            return "🔒 Locked capsule";
        }
        if (message.getMessageType() == MessageType.PHOTO) {
            return message.getContent() != null && !message.getContent().isBlank()
                    ? "📷 " + message.getContent()
                    : "📷 Photo";
        }
        if (message.getMessageType() == MessageType.VIDEO) {
            return message.getContent() != null && !message.getContent().isBlank()
                    ? "🎬 " + message.getContent()
                    : "🎬 Video";
        }
        return truncate(message.getContent(), 140);
    }
}
