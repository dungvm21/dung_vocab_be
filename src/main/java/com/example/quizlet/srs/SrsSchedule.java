package com.example.quizlet.srs;

import com.example.quizlet.entity.ReviewStatus;

import java.time.Instant;

/**
 * Immutable result of one SRS scheduling step — applied to the
 * persisted {@link com.example.quizlet.entity.UserCardProgress} by the service.
 */
public record SrsSchedule(
        int intervalDays,
        int repetition,
        double easeFactor,
        Instant nextReviewDate,
        ReviewStatus status
) {
}
