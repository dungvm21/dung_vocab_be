package com.example.quizlet.dto.auth;

/**
 * Response payload for successful registration/login/refresh.
 * accessToken: short-lived signed JWT. refreshToken: opaque, rotatable —
 * send it to /api/auth/refresh when the access token expires.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long id,
        String username,
        String email,
        String role
) {
    public static AuthResponse of(String accessToken, String refreshToken, com.example.quizlet.entity.User user) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
