package com.example.quizlet.service.impl;

import com.example.quizlet.entity.RefreshToken;
import com.example.quizlet.entity.User;
import com.example.quizlet.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RefreshTokenServiceImpl}: issue (hash-only storage),
 * validateAndRotate (rotation + reuse detection), revoke (idempotent logout).
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long TTL_DAYS = 7;

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenServiceImpl service;

    private final User user = User.builder().id(USER_ID).username("dung").build();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(repository, TTL_DAYS);
    }

    // ---------- issue ----------

    @Test
    @DisplayName("issue: returns a 256-bit Base64-URL raw token and stores only its SHA-256 hash")
    void issueStoresHashNotRawToken() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(user);

        assertThat(raw).hasSize(43); // 32 bytes → 43 Base64-URL chars, no padding
        assertThat(raw).matches("^[A-Za-z0-9_-]+$");
        assertThat(raw).doesNotContain("=");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(raw));
        assertThat(saved.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("issue: expiry = now + TTL (7 days)")
    void issueSetsExpiryFromTtl() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        service.issue(user);
        Instant after = Instant.now();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        Instant expiresAt = captor.getValue().getExpiresAt();

        assertThat(expiresAt).isAfterOrEqualTo(before.plus(Duration.ofDays(TTL_DAYS)));
        assertThat(expiresAt).isBeforeOrEqualTo(after.plus(Duration.ofDays(TTL_DAYS)));
    }

    @Test
    @DisplayName("issue: two calls never produce the same raw token (randomness)")
    void issueProducesUniqueTokens() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String raw1 = service.issue(user);
        String raw2 = service.issue(user);

        assertThat(raw1).isNotEqualTo(raw2);
    }

    // ---------- validateAndRotate ----------

    @Test
    @DisplayName("validateAndRotate: live token → returns its user and burns the token")
    void rotateLiveTokenReturnsUserAndRevokes() {
        String raw = "raw-token";
        RefreshToken token = liveToken(raw);
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

        User result = service.validateAndRotate(raw);

        assertThat(result).isSameAs(user);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isNotNull();
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    @DisplayName("validateAndRotate: unknown token → BadCredentialsException")
    void rotateUnknownTokenThrows() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndRotate("no-such-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    @DisplayName("validateAndRotate: reused (revoked) token → revokes the user's whole family, then 401")
    void rotateRevokedTokenTriggersFamilyRevocation() {
        String raw = "burnt-token";
        RefreshToken token = liveToken(raw);
        token.revoke(Instant.now().minusSeconds(60)); // already used once
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.validateAndRotate(raw))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");

        verify(repository).revokeAllForUser(eq(USER_ID), any(Instant.class));
    }

    @Test
    @DisplayName("validateAndRotate: expired but not revoked → 401, family untouched")
    void rotateExpiredTokenThrowsWithoutFamilyRevocation() {
        String raw = "expired-token";
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.validateAndRotate(raw))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");

        verify(repository, never()).revokeAllForUser(any(), any());
    }

    // ---------- revoke (logout) ----------

    @Test
    @DisplayName("revoke: live token → revoked (logout)")
    void revokeRevokesLiveToken() {
        String raw = "live-token";
        RefreshToken token = liveToken(raw);
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

        service.revoke(raw);

        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("revoke: unknown token → silent no-op (idempotent logout)")
    void revokeUnknownTokenIsNoOp() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("whatever");

        verify(repository, never()).save(any());
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    @DisplayName("revoke: already-revoked token → left untouched")
    void revokeAlreadyRevokedTokenKeepsOriginalTimestamp() {
        String raw = "old-token";
        RefreshToken token = liveToken(raw);
        Instant original = Instant.now().minusSeconds(3600);
        token.revoke(original);
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

        service.revoke(raw);

        assertThat(token.getRevokedAt()).isEqualTo(original);
    }

    // ---------- helpers ----------

    private RefreshToken liveToken(String raw) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .expiresAt(Instant.now().plus(Duration.ofDays(TTL_DAYS)))
                .build();
    }

    /** Mirrors RefreshTokenServiceImpl.sha256Hex — expected storage format. */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
