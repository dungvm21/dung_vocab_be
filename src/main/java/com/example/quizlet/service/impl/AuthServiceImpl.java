package com.example.quizlet.service.impl;

import com.example.quizlet.dto.auth.AuthResponse;
import com.example.quizlet.dto.auth.LoginRequest;
import com.example.quizlet.dto.auth.RegisterRequest;
import com.example.quizlet.dto.auth.TokenRefreshRequest;
import com.example.quizlet.entity.Role;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.DuplicateResourceException;
import com.example.quizlet.repository.UserRepository;
import com.example.quizlet.security.JwtService;
import com.example.quizlet.service.AuthService;
import com.example.quizlet.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AuthService}: registration, login, refresh-token
 * rotation, and logout. Issues a short-lived JWT access token plus an
 * opaque, DB-backed refresh token on every successful authentication.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Creates a new user with ROLE_USER and issues a token pair.
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username is already taken: " + request.username());
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email is already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                // Never persist the raw password.
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .build();

        user = userRepository.save(user);
        return issueTokens(user);
    }

    /**
     * Authenticates by username or email, then issues a token pair.
     * Throws BadCredentialsException (mapped to 401) on failure.
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByUsername(request.usernameOrEmail())
                    .or(() -> userRepository.findByEmail(request.usernameOrEmail()))
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.password()));
            User principal = (User) authentication.getPrincipal();
            return issueTokens(principal);
        } catch (AuthenticationException e) {
            // Uniform message so attackers cannot learn whether the account exists.
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    /**
     * Exchanges a valid refresh token for a fresh pair (rotation happens inside
     * RefreshTokenService — the presented token is burnt on use).
     */
    @Override
    @Transactional
    public AuthResponse refresh(TokenRefreshRequest request) {
        User user = refreshTokenService.validateAndRotate(request.refreshToken());
        return issueTokens(user);
    }

    /**
     * Revokes the refresh token. Idempotent — unknown tokens are ignored.
     * The current access token simply expires after ≤ its short TTL.
     */
    @Override
    @Transactional
    public void logout(TokenRefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return AuthResponse.of(accessToken, refreshToken, user);
    }
}
