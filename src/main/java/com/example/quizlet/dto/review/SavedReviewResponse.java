package com.example.quizlet.dto.review;

import com.example.quizlet.entity.ReviewStatus;
import com.example.quizlet.entity.UserCardProgress;

import java.time.Instant;

/**
 * Result of saving a single card to the review queue (due immediately).
 */
public record SavedReviewResponse(
        Long cardId,
        boolean saved,          // true = newly added, false = already in queue
        ReviewStatus status,
        Instant nextReviewDate
) {
    public static SavedReviewResponse of(UserCardProgress progress, boolean newlySaved) {
        return new SavedReviewResponse(
                progress.getCard().getId(),
                newlySaved,
                progress.getStatus(),
                progress.getNextReviewDate()
        );
    }
}
