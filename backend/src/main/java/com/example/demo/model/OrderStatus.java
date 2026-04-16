package com.example.demo.model;

/*
 * Order states:
 * PENDING -> just created, not shipped yet
 * SHIPPED -> operator marked it as shipped
 * OUT_OF_STOCK -> wholesaler doesn't have enough stock
 * CANCELLED -> customer cancelled it
 */
public enum OrderStatus {
    PENDING,
    SHIPPED,
    OUT_OF_STOCK,
    CANCELLED
}