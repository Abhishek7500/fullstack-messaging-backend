package com.messenger.service;

import com.messenger.domain.ChatMessage;
import com.messenger.domain.Conversation;
import com.messenger.domain.ConversationParticipant;
import com.messenger.domain.ConversationType;
import com.messenger.domain.UserAccount;
import com.messenger.dto.ChatEvents.CreateConversationRequest;
import com.messenger.dto.ConversationDto;
import com.messenger.dto.MessageDto;
import com.messenger.dto.UserDto;
import com.messenger.repo.ChatMessageRepository;
import com.messenger.repo.ConversationParticipantRepository;
import com.messenger.repo.ConversationRepository;
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
import java.util.Set;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserAccountRepository userAccountRepository;
    private final PresenceService presenceService;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            ChatMessageRepository chatMessageRepository,
            UserAccountRepository userAccountRepository,
            PresenceService presenceService
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userAccountRepository = userAccountRepository;
        this.presenceService = presenceService;
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
        requireParticipant(conversationId, sender.getId());
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content.trim());
        ChatMessage saved = chatMessageRepository.save(message);

        conversation.setLastMessagePreview(truncate(saved.getContent(), 140));
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
        return chatMessageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(safePage, safeSize))
                .map(MessageDto::from);
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
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
