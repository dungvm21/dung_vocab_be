package com.example.quizlet.service.impl;

import com.example.quizlet.entity.RefreshToken;
import com.example.quizlet.entity.User;
import com.example.quizlet.repository.RefreshTokenRepository;
import com.example.quizlet.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Implementation of {@link RefreshTokenService}.
 * <p>
 * Security properties:
 *  - raw token: 256 bits of SecureRandom, Base64-URL — unguessable, shown once
 *  - storage: only SHA-256(token) — DB leak yields useless hashes
 *  - rotation: every successful refresh burns the presented token
 *  - reuse detection: presenting a burnt token revokes every token of the user
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository repository;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenServiceImpl(RefreshTokenRepository repository,
                                   @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays) {
        this.repository = repository;
        this.refreshTtl = Duration.ofDays(refreshExpirationDays);
    }

    @Override
    @Transactional
    public String issue(User user) {
        String raw = generateRawToken();
        repository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .expiresAt(Instant.now().plus(refreshTtl))
                .build());
        return raw;
    }

    @Override
    @Transactional
    public User validateAndRotate(String rawRefreshToken) {
        RefreshToken token = repository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        Instant now = Instant.now();
        if (token.isRevoked()) {
            // Token already burnt after use → replay/theft suspect.
            // Kill every remaining session of this user, then refuse.
            repository.revokeAllForUser(token.getUser().getId(), now);
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (token.isExpired(now)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        token.revoke(now); // rotation — the presented token is burnt
        return token.getUser();
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        repository.findByTokenHash(sha256Hex(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
