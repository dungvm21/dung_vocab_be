package com.example.quizlet.dto.study;

/**
 * Aggregated progress over one deck for the current user.
 * Cards without a progress row count as NOT_LEARNED.
 */
public record DeckProgressResponse(
        long total,
        long mastered,
        long stillLearning,
        long notLearned,
        double percent      // mastered / total * 100, 1 decimal
) {
}
