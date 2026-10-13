package com.messenger.repo;

import com.messenger.domain.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);

    /** Raw {@code [reactionType, userId, messageId]} rows for tallying. */
    @Query("""
            SELECT r.reactionType, r.user.id, r.message.id
            FROM Reaction r
            WHERE r.message.id IN :messageIds
            """)
    List<Object[]> findRawByMessageIds(@Param("messageIds") List<Long> messageIds);

    boolean existsByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);
}
