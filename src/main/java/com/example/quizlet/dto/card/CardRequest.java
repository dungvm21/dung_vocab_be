package com.example.quizlet.dto.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating/updating a card.
 */
public record CardRequest(
        @NotBlank
        @Size(max = 255)
        String term,

        @NotBlank
        @Size(max = 2000)
        String definition,

        @Size(max = 1000)
        String hint
) {
}
