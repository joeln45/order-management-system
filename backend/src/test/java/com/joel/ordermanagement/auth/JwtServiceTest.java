package com.joel.ordermanagement.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}. No Mockito needed: the service has a
 * plain constructor we can call directly with test values, so each test gets
 * its own fresh instance.
 */
class JwtServiceTest {

    private static final String SECRET = "this-is-a-test-secret-of-sufficient-length-1234567890";
    private static final String ISSUER = "order-management-test";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private JwtService jwt;
    private User operator;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRET, ISSUER, ACCESS_TTL, REFRESH_TTL);
        operator = new User("operator", "bcrypt-hash-doesnt-matter-here", Role.OPERATOR);
        operator.setId("user-1");
    }

    // =============================================================
    // Constructor validation
    // =============================================================

    @Test
    @DisplayName("constructor rejects a secret shorter than 32 characters")
    void constructor_shortSecret_throws() {
        assertThatThrownBy(() -> new JwtService("too-short", ISSUER, ACCESS_TTL, REFRESH_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    @DisplayName("constructor rejects a null secret")
    void constructor_nullSecret_throws() {
        assertThatThrownBy(() -> new JwtService(null, ISSUER, ACCESS_TTL, REFRESH_TTL))
                .isInstanceOf(IllegalStateException.class);
    }

    // =============================================================
    // Access token issue + verify
    // =============================================================

    @Test
    @DisplayName("issued access token verifies and carries sub / role / username / issuer")
    void issueAccessToken_roundTripsAllClaims() {
        String token = jwt.issueAccessToken(operator);

        Optional<DecodedJWT> decoded = jwt.verify(token);

        assertThat(decoded).isPresent();
        DecodedJWT jwtDecoded = decoded.get();
        assertThat(jwtDecoded.getSubject()).isEqualTo("user-1");
        assertThat(jwtDecoded.getClaim("username").asString()).isEqualTo("operator");
        assertThat(jwtDecoded.getClaim("role").asString()).isEqualTo("OPERATOR");
        assertThat(jwtDecoded.getIssuer()).isEqualTo(ISSUER);
        assertThat(jwtDecoded.getExpiresAt()).isAfter(new java.util.Date());
    }

    @Test
    @DisplayName("a token signed with a different secret fails verification")
    void verify_tamperedSignature_returnsEmpty() {
        JwtService other = new JwtService(
                "a-completely-different-secret-that-is-long-enough!!", ISSUER, ACCESS_TTL, REFRESH_TTL);
        String tokenFromOther = other.issueAccessToken(operator);

        // same issuer + shape, different signing key → verify fails
        assertThat(jwt.verify(tokenFromOther)).isEmpty();
    }

    @Test
    @DisplayName("a token from a different issuer fails verification")
    void verify_wrongIssuer_returnsEmpty() {
        JwtService wrongIssuer = new JwtService(SECRET, "some-other-service", ACCESS_TTL, REFRESH_TTL);
        String token = wrongIssuer.issueAccessToken(operator);

        assertThat(jwt.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("an expired token fails verification")
    void verify_expiredToken_returnsEmpty() throws InterruptedException {
        // TTL of 1ms, so the token is already expired by the time we try to verify it.
        JwtService shortLived = new JwtService(SECRET, ISSUER, Duration.ofMillis(1), REFRESH_TTL);
        String token = shortLived.issueAccessToken(operator);
        Thread.sleep(50);  // ensure wall-clock has moved past expiry

        assertThat(shortLived.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("verify returns empty for complete garbage input")
    void verify_garbage_returnsEmpty() {
        assertThat(jwt.verify("not.a.jwt")).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
        assertThat(jwt.verify("xxxxx")).isEmpty();
    }

    // =============================================================
    // Refresh token generation
    // =============================================================

    @Test
    @DisplayName("refresh tokens are URL-safe base64 and unique across calls")
    void generateRefreshTokenRaw_uniqueAndUrlSafe() {
        String a = jwt.generateRefreshTokenRaw();
        String b = jwt.generateRefreshTokenRaw();

        assertThat(a).isNotEqualTo(b);
        // url-safe base64 alphabet: [A-Za-z0-9_-], no padding
        assertThat(a).matches("[A-Za-z0-9_-]+");
        // 32 bytes → ceil(32*4/3) = 43 chars without padding
        assertThat(a).hasSize(43);
    }

    @Test
    @DisplayName("accessor TTLs match the values passed to the constructor")
    void ttlAccessors_returnConfiguredValues() {
        assertThat(jwt.getAccessTtl()).isEqualTo(ACCESS_TTL);
        assertThat(jwt.getRefreshTtl()).isEqualTo(REFRESH_TTL);
    }
}
