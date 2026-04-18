package com.joel.ordermanagement.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * Incoming body for {@code POST /orders}. A single request can contain many
 * products — the cart-style checkout added in Phase 3. {@code @Valid} on the
 * {@link LineItem} list instructs the validator to recurse into each element
 * so per-line field rules are enforced too.
 */
@Data
public class CreateOrderRequest {

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<LineItem> items;

    /** One line in the incoming cart. */
    @Data
    public static class LineItem {

        @NotBlank(message = "productId is required")
        private String productId;

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        private Integer quantity;
    }
}
