package com.example.quizlet.dto.study;

import com.example.quizlet.entity.CardProgress;

/**
 * Per-card progress view (star toggle, single-card state).
 */
public record CardProgressResponse(
        Long cardId,
        String state,
        int consecutiveCorrect,
        boolean starred
) {
    public static CardProgressResponse from(CardProgress progress) {
        return new CardProgressResponse(
                progress.getCard().getId(),
                progress.getState().name(),
                progress.getConsecutiveCorrect(),
                progress.isStarred()
        );
    }
}
