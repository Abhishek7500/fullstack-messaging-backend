package com.messenger.repo;

import com.messenger.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT u FROM UserAccount u
            WHERE u.id <> :currentUserId
              AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY u.displayName ASC
            """)
    List<UserAccount> searchUsers(@Param("currentUserId") Long currentUserId, @Param("q") String q);

    List<UserAccount> findByIdNotOrderByDisplayNameAsc(Long currentUserId);
}
