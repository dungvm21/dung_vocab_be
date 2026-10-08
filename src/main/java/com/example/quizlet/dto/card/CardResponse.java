package com.example.quizlet.dto.card;

import com.example.quizlet.entity.Card;

/**
 * Response payload for a card.
 */
public record CardResponse(
        Long id,
        String term,
        String definition,
        String hint,
        Long deckId
) {
    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getTerm(),
                card.getDefinition(),
                card.getHint(),
                card.getDeck().getId()
        );
    }
}
