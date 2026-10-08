package com.example.quizlet.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for POST /api/auth/login.
 * {@code usernameOrEmail} accepts either the username or the email address.
 */
public record LoginRequest(
        @NotBlank
        String usernameOrEmail,

        @NotBlank
        String password
) {
}
