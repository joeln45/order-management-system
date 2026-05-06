package com.joel.ordermanagement.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies JWT access tokens and generates opaque refresh tokens.
 * <p>
 * Access tokens: short-lived, self-contained, HMAC-SHA256 signed, include
 * {@code sub}, {@code role} and {@code iss}. Refresh tokens: 32 random bytes
 * base64-encoded, with no structure and meaningless without the server-side record.
 */
@Slf4j
@Service
public class JwtService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-ttl}") Duration accessTtl,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (HMAC-SHA256 key length)");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(issuer).build();
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    // ------------------------------------------------------------
    // Access tokens (JWT)
    // ------------------------------------------------------------

    /** Mint a fresh access token for a user. */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(user.getId())
                .withClaim("username", user.getUsername())
                .withClaim("role", user.getRole().name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(accessTtl)))
                .sign(algorithm);
    }

    /** Parse + verify a token; returns empty on any failure (expired, bad signature, wrong issuer). */
    public Optional<DecodedJWT> verify(String token) {
        try {
            return Optional.of(verifier.verify(token));
        } catch (JWTVerificationException e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------
    // Refresh tokens (opaque)
    // ------------------------------------------------------------

    /**
     * Generate a cryptographically-random 256-bit refresh token.
     * The raw value is shown to the client once; only its hash is stored.
     */
    public String generateRefreshTokenRaw() {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }
}
