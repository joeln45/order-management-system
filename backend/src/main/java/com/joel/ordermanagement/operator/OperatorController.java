package com.joel.ordermanagement.operator;

import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.NotFoundException;
import com.joel.ordermanagement.order.OrderResponse;
import com.joel.ordermanagement.order.OrderService;
import com.joel.ordermanagement.order.OrderStatus;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Operator-only REST API: manage orders, prices and analytics.
 * <p>
 * All authorisation is enforced upstream by
 * {@link com.joel.ordermanagement.config.SecurityConfig} — every request to
 * {@code /operator/**} requires {@code ROLE_OPERATOR}. Login lives at
 * {@code /auth/login}; the response carries the role so the UI can decide
 * where to send the user next.
 */
@RestController
@RequestMapping("/operator")
@RequiredArgsConstructor
@Tag(name = "Operator",
     description = "Operator-only endpoints: manage orders, update prices, view analytics. Requires ROLE_OPERATOR.")
public class OperatorController {

    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    // ------------------------------------------------------------
    // Order management
    // ------------------------------------------------------------

    @GetMapping("/orders")
    @Operation(summary = "List all orders across all customers")
    @ApiResponse(responseCode = "200", description = "All orders with status-update and customer-revenue links")
    public ResponseEntity<CollectionModel<EntityModel<OrderResponse>>> getAllOrders() {
        List<EntityModel<OrderResponse>> orderModels = orderService.getAllOrders().stream()
                .map(order -> {
                    OrderResponse body = OrderResponse.from(order);
                    EntityModel<OrderResponse> model = EntityModel.of(body);
                    model.add(linkTo(methodOn(OperatorController.class)
                            .updateOrderStatus(body.getId(), null)).withRel("update-status"));
                    model.add(linkTo(methodOn(OperatorController.class)
                            .getCustomerRevenue(body.getCustomerId())).withRel("customer-revenue"));
                    return model;
                })
                .collect(Collectors.toList());

        CollectionModel<EntityModel<OrderResponse>> collection = CollectionModel.of(orderModels);
        collection.add(linkTo(methodOn(OperatorController.class).getAllOrders()).withSelfRel());
        return ResponseEntity.ok(collection);
    }

    @PutMapping("/orders/{id}/status")
    @Operation(
            summary = "Update an order's status",
            description = "Valid transitions: PENDING → PAID → SHIPPED → DELIVERED, or any → CANCELLED.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Missing status in body"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<Void> updateOrderStatus(
            @Parameter(description = "Order id", example = "ord-42") @PathVariable String id,
            @RequestBody StatusUpdate statusUpdate) {

        if (statusUpdate == null || statusUpdate.getStatus() == null) {
            throw new BusinessRuleException("Missing status in request body");
        }
        orderService.updateOrderStatus(id, statusUpdate.getStatus());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // Product management
    // ------------------------------------------------------------

    @PutMapping("/products/{id}/price")
    @Operation(
            summary = "Update a product's retail price",
            description = "Does not affect the priceAtPurchase snapshot on existing orders — historical orders keep their old totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Price updated"),
            @ApiResponse(responseCode = "400", description = "Missing retailPrice in body"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<Void> updateProductPrice(
            @Parameter(description = "Product id", example = "prod-123") @PathVariable String id,
            @RequestBody PriceUpdate priceUpdate) {

        if (priceUpdate == null || priceUpdate.getRetailPrice() == null) {
            throw new BusinessRuleException("Missing retailPrice in request body");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));

        product.setRetailPrice(priceUpdate.getRetailPrice());
        productRepository.save(product);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // Customer analytics
    // ------------------------------------------------------------

    @GetMapping("/customers/{id}/revenue")
    @Operation(summary = "Get total lifetime revenue from a customer",
               description = "Sums priceAtPurchase × quantity across all non-CANCELLED orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revenue total"),
            @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    public ResponseEntity<RevenueResponse> getCustomerRevenue(
            @Parameter(description = "Customer id", example = "CUST001") @PathVariable String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Customer", id));

        BigDecimal revenue = orderService.calculateCustomerRevenue(id);
        return ResponseEntity.ok(new RevenueResponse(id, customer.getName(), revenue));
    }

    // ------------------------------------------------------------
    // Request/response DTOs
    // ------------------------------------------------------------

    @Data
    @Schema(description = "New status for an order")
    public static class StatusUpdate {
        @Schema(description = "Target status", example = "SHIPPED")
        private OrderStatus status;
    }

    @Data
    @Schema(description = "New retail price for a product")
    public static class PriceUpdate {
        @Schema(description = "Retail price in GBP (2 d.p.)", example = "129.99")
        private BigDecimal retailPrice;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "Lifetime revenue from a single customer")
    public static class RevenueResponse {
        @Schema(example = "CUST001") private String customerId;
        @Schema(example = "Alice Example") private String customerName;
        @Schema(example = "1284.50") private BigDecimal totalRevenue;
    }
}
