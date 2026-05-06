package com.joel.ordermanagement.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * Incoming body for {@code POST /orders}. A single request can contain many
 * products: the cart-style checkout added in Phase 3. {@code @Valid} on the
 * {@link LineItem} list instructs the validator to recurse into each element
 * so per-line field rules are enforced too.
 */
@Data
@Schema(description = "Multi-item order creation payload")
public class CreateOrderRequest {

    @NotBlank(message = "customerId is required")
    @Schema(description = "Existing customer id", example = "CUST001")
    private String customerId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    @Schema(description = "One entry per product in the cart (minimum 1).")
    private List<LineItem> items;

    /** One line in the incoming cart. */
    @Data
    @Schema(description = "A single product + quantity in the cart")
    public static class LineItem {

        @NotBlank(message = "productId is required")
        @Schema(description = "Existing product id", example = "prod-123")
        private String productId;

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        @Schema(description = "Quantity (must be > 0)", example = "2")
        private Integer quantity;
    }
}
