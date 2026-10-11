package com.example.quizlet.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for POST /api/auth/refresh and POST /api/auth/logout.
 */
public record TokenRefreshRequest(
        @NotBlank
        String refreshToken
) {
}
