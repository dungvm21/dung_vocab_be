package com.example.quizlet.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for POST /api/reviews/{cardId}.
 * Quality score: 1=Again (forgot), 2=Hard, 3=Good, 4=Easy.
 */
public record ReviewRequest(
        @NotNull
        @Min(1)
        @Max(4)
        Integer quality
) {
}
