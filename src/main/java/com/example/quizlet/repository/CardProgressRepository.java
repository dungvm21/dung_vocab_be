package com.example.quizlet.repository;

import com.example.quizlet.entity.CardProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CardProgressRepository extends JpaRepository<CardProgress, Long> {

    Optional<CardProgress> findByUserIdAndCardId(Long userId, Long cardId);

    /** Bulk load for quiz generation / progress stats over one deck's cards. */
    List<CardProgress> findByUserIdAndCardIdIn(Long userId, Collection<Long> cardIds);
}
