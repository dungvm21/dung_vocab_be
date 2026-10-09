package com.example.quizlet.dto.study;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for POST /api/decks/{deckId}/study/answer.
 * {@code selectedCardId} is the option the user picked (null = skipped);
 * the server compares it with cardId — never trusts a client-side "correct" flag.
 */
public record StudyAnswerRequest(
        @NotNull
        Long cardId,

        Long selectedCardId,

        /** Optional; defaults to MULTIPLE_CHOICE. */
        String mode
) {
}
