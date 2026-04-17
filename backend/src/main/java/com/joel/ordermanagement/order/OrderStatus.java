package com.joel.ordermanagement.order;

/**
 * Order lifecycle states.
 * <ul>
 *   <li>{@link #PENDING} — created by customer, not yet shipped.</li>
 *   <li>{@link #SHIPPED} — operator has dispatched the order.</li>
 *   <li>{@link #OUT_OF_STOCK} — wholesaler can no longer fulfil.</li>
 *   <li>{@link #CANCELLED} — customer cancelled while still pending.</li>
 * </ul>
 */
public enum OrderStatus {
    PENDING,
    SHIPPED,
    OUT_OF_STOCK,
    CANCELLED
}
