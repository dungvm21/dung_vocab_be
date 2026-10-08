package com.example.quizlet.dto.auth;

/**
 * Response payload for successful registration/login.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Long id,
        String username,
        String email,
        String role
) {
    public static AuthResponse of(String token, com.example.quizlet.entity.User user) {
        return new AuthResponse(
                token,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
