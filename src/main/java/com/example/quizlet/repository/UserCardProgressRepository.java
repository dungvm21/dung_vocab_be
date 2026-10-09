package com.example.quizlet.repository;

import com.example.quizlet.entity.UserCardProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCardProgressRepository extends JpaRepository<UserCardProgress, Long> {

    Optional<UserCardProgress> findByUserIdAndCardId(Long userId, Long cardId);

    /** Bulk lookup for deck enrollment (which cards are already in the review queue). */
    List<UserCardProgress> findByUserIdAndCardIdIn(Long userId, Collection<Long> cardIds);

    /** Due queue: cards of the user whose next review time has arrived, oldest due first. */
    @Query("""
            select p from UserCardProgress p
            join fetch p.card
            where p.user.id = :userId and p.nextReviewDate <= :now
            order by p.nextReviewDate asc
            """)
    List<UserCardProgress> findDue(@Param("userId") Long userId,
                                   @Param("now") Instant now,
                                   Pageable pageable);
}
