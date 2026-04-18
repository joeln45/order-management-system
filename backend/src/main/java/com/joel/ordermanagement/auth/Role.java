package com.joel.ordermanagement.auth;

/**
 * Application roles. Stored as strings in the database (via
 * {@code @Enumerated(EnumType.STRING)}) so that reordering or inserting
 * new values later can't silently corrupt existing rows.
 */
public enum Role {
    CUSTOMER,
    OPERATOR
}
