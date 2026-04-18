package com.joel.ordermanagement.exception;

/**
 * Thrown when credentials are missing, wrong, or expired. Translated to HTTP 401
 * by {@link GlobalExceptionHandler}. Distinct from Spring Security's own
 * {@code AuthenticationException} — we use this one for application-layer
 * auth failures (bad password, expired refresh token).
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
