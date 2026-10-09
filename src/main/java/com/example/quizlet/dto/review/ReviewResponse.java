package com.example.quizlet.dto.review;

import com.example.quizlet.entity.ReviewStatus;
import com.example.quizlet.entity.UserCardProgress;

import java.time.Instant;

/**
 * Result of submitting a review: fresh SRS schedule for the card.
 */
public record ReviewResponse(
        Long cardId,
        int quality,
        ReviewStatus status,
        int intervalDays,
        double easeFactor,
        int repetition,
        Instant nextReviewDate
) {
    public static ReviewResponse from(UserCardProgress progress, int quality) {
        return new ReviewResponse(
                progress.getCard().getId(),
                quality,
                progress.getStatus(),
                progress.getIntervalDays(),
                progress.getEaseFactor(),
                progress.getRepetition(),
                progress.getNextReviewDate()
        );
    }
}
