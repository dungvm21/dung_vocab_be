package com.example.quizlet.service;

import com.example.quizlet.dto.auth.AuthResponse;
import com.example.quizlet.dto.auth.LoginRequest;
import com.example.quizlet.dto.auth.RegisterRequest;

/**
 * Authentication contract: registration and login (JWT issuance).
 */
public interface AuthService {

    /** Creates a new user with ROLE_USER and issues a JWT immediately. */
    AuthResponse register(RegisterRequest request);

    /** Authenticates by username or email, then issues a JWT. */
    AuthResponse login(LoginRequest request);
}
