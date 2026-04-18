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
public class OperatorController {

    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    // ------------------------------------------------------------
    // Order management
    // ------------------------------------------------------------

    @GetMapping("/orders")
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
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable String id,
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
    public ResponseEntity<Void> updateProductPrice(
            @PathVariable String id,
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
    public ResponseEntity<RevenueResponse> getCustomerRevenue(@PathVariable String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Customer", id));

        BigDecimal revenue = orderService.calculateCustomerRevenue(id);
        return ResponseEntity.ok(new RevenueResponse(id, customer.getName(), revenue));
    }

    // ------------------------------------------------------------
    // Request/response DTOs
    // ------------------------------------------------------------

    @Data
    public static class StatusUpdate {
        private OrderStatus status;
    }

    @Data
    public static class PriceUpdate {
        private BigDecimal retailPrice;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RevenueResponse {
        private String customerId;
        private String customerName;
        private BigDecimal totalRevenue;
    }
}
