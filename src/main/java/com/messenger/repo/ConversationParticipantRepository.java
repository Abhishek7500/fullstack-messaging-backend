package com.messenger.repo;

import com.messenger.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    @EntityGraph(attributePaths = "user")
    List<ConversationParticipant> findByConversationId(Long conversationId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("""
            SELECT p FROM ConversationParticipant p
            JOIN FETCH p.conversation c
            JOIN FETCH p.user
            WHERE p.user.id = :userId
            ORDER BY COALESCE(c.lastMessageAt, c.updatedAt) DESC
            """)
    List<ConversationParticipant> findInboxForUser(@Param("userId") Long userId);
}
