package com.example.quizlet.repository;

import com.example.quizlet.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Reuse detection: kill every live token of the user (theft suspect).
     * Runs in its own transaction and commits immediately — the caller throws
     * right after this, which would otherwise roll the revocation back.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.user.id = :userId and t.revokedAt is null
            """)
    void revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
