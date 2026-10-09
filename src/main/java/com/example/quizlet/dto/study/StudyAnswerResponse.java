package com.example.quizlet.dto.study;

import com.example.quizlet.learning.WordState;

/**
 * Grading result for a single answer, with fresh deck-wide progress.
 */
public record StudyAnswerResponse(
        boolean correct,
        Long cardId,
        Long correctCardId,
        String correctTerm,
        WordState state,
        int consecutiveCorrect,
        DeckProgressResponse deckProgress
) {
}
