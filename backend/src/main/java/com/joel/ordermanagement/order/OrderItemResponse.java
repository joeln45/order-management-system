package com.joel.ordermanagement.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API shape for a single line in an {@link OrderResponse}. Includes the
 * product description so UIs can render a cart without a second fetch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private String productId;
    private String productDescription;
    private Integer quantity;
    private BigDecimal priceAtPurchase;
    private BigDecimal lineTotal;

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getDescription(),
                item.getQuantity(),
                item.getPriceAtPurchase(),
                item.lineTotal()
        );
    }
}
