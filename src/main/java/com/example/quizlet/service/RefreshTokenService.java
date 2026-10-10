package com.example.quizlet.service;

import com.example.quizlet.entity.User;

/**
 * Refresh-token contract: issuance, rotation with reuse detection, and revocation.
 * Raw tokens are returned exactly once and never stored — only SHA-256 hashes persist.
 */
public interface RefreshTokenService {

    /** Issues a new refresh token for the user; returns the raw value (shown once). */
    String issue(User user);

    /**
     * Validates a raw refresh token and rotates it (marks it used).
     * Returns the owning user. A reused (already-revoked) token revokes ALL
     * tokens of that user before failing.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         if unknown, expired or revoked
     */
    User validateAndRotate(String rawRefreshToken);

    /** Revokes a raw refresh token. Unknown tokens are ignored (idempotent logout). */
    void revoke(String rawRefreshToken);
}
