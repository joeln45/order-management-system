package com.joel.ordermanagement.exception;

/**
 * Thrown when the request is syntactically valid but violates a domain rule —
 * insufficient stock, unprofitable retail price, cancelling a shipped order,
 * duplicate username, etc. Translated to HTTP 409 Conflict by
 * {@link GlobalExceptionHandler} (the semantic fit for "state conflict with rule").
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
