package com.joel.ordermanagement.exception;

/**
 * Thrown when a requested resource (customer, product, order, user...) does not exist.
 * Translated to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    /** Convenience factory: {@code NotFoundException.of("Customer", id)} → "Customer not found: id". */
    public static NotFoundException of(String resource, String id) {
        return new NotFoundException(resource + " not found: " + id);
    }
}
