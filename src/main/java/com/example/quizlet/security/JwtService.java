package com.example.quizlet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * Stateless JWT helper: issues and validates HS256-signed tokens.
 * The signing secret comes from configuration (Base64-encoded, >= 256 bits).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret.getBytes(StandardCharsets.UTF_8)));
        this.expirationMs = expirationMs;
    }

    /** Generates a signed token whose subject is the username. */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /** Returns the username (subject) claim, or null if the token is invalid/expired. */
    public String extractUsername(String token) {
        Claims claims = parse(token);
        return claims != null ? claims.getSubject() : null;
    }

    /** True if the token is well-formed, unexpired and belongs to the given user. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = parse(token);
        if (claims == null) {
            return false;
        }
        return userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }

    /** Parses and verifies the signature; returns null instead of throwing on any failure. */
    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // Malformed, tampered or expired tokens all land here and are simply rejected.
            return null;
        }
    }
}
