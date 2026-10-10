package com.example.quizlet.service;

import com.example.quizlet.dto.auth.AuthResponse;
import com.example.quizlet.dto.auth.LoginRequest;
import com.example.quizlet.dto.auth.RegisterRequest;
import com.example.quizlet.dto.auth.TokenRefreshRequest;

/**
 * Authentication contract: registration, login (JWT + refresh token),
 * token refresh with rotation, and logout.
 */
public interface AuthService {

    /** Creates a new user with ROLE_USER and issues access + refresh tokens. */
    AuthResponse register(RegisterRequest request);

    /** Authenticates by username or email, then issues access + refresh tokens. */
    AuthResponse login(LoginRequest request);

    /**
     * Exchanges a valid refresh token for a new token pair (rotation:
     * the presented token is burnt). Reuse of a burnt token revokes all
     * of the user's refresh tokens.
     */
    AuthResponse refresh(TokenRefreshRequest request);

    /** Revokes the given refresh token. Idempotent. */
    void logout(TokenRefreshRequest request);
}
