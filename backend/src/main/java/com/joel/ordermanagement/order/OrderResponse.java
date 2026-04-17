package com.joel.ordermanagement.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API response shape for an Order.
 * <p>
 * The internal {@link Order} entity now holds nested {@code Customer} and
 * {@code Product} objects (real JPA relationships). This DTO flattens those
 * back to {@code customerId} / {@code productId} strings so the public JSON
 * contract stays stable across the persistence overhaul.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String customerId;
    private String productId;
    private Integer quantity;
    private OrderStatus status;
    private LocalDateTime orderDate;

    /** Map a persistence entity into its public API shape. */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getProduct().getId(),
                order.getQuantity(),
                order.getStatus(),
                order.getOrderDate()
        );
    }
}
