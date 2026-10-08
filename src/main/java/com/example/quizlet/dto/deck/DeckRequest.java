package com.example.quizlet.dto.deck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating/updating a deck.
 */
public record DeckRequest(
        @NotBlank
        @Size(max = 150)
        String title,

        @Size(max = 1000)
        String description,

        @Size(max = 100)
        String category,

        boolean isPublic
) {
}
