package com.example.quizlet.dto.deck;

import com.example.quizlet.entity.Deck;

import java.time.Instant;

/**
 * Response payload for a deck. Card list is intentionally excluded —
 * cards are fetched via the dedicated /api/decks/{id}/cards endpoint.
 */
public record DeckResponse(
        Long id,
        String title,
        String description,
        String category,
        boolean isPublic,
        Long creatorId,
        String creatorUsername,
        Instant createdAt
) {
    public static DeckResponse from(Deck deck) {
        return new DeckResponse(
                deck.getId(),
                deck.getTitle(),
                deck.getDescription(),
                deck.getCategory(),
                deck.isPublic(),
                deck.getCreator().getId(),
                deck.getCreator().getUsername(),
                deck.getCreatedAt()
        );
    }
}
