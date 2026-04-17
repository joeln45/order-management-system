package com.joel.ordermanagement.operator;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.order.Order;
import com.joel.ordermanagement.order.OrderResponse;
import com.joel.ordermanagement.order.OrderService;
import com.joel.ordermanagement.order.OrderStatus;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Operator REST API: privileged endpoints for managing orders, prices and analytics.
 * Authentication is currently a JWT issued from {@code POST /operator/login}.
 * Phase 4 replaces this with a Spring Security filter chain and refresh tokens.
 */
@RestController
@CrossOrigin
@RequestMapping("/operator")
public class OperatorController {

    /** Hardcoded HMAC secret. Phase 4 sources this from environment config. */
    private static final String SECRET_KEY =
            "order-management-secret-key-must-be-at-least-32-characters-long";

    /** Hardcoded operator credentials. Phase 4 stores hashed credentials in the DB. */
    private static final String OPERATOR_USERNAME = "operator";
    private static final String OPERATOR_PASSWORD = "password123";

    /** Access token TTL (2 hours). Phase 4 introduces short access + refresh tokens. */
    private static final long TOKEN_TTL_MS = 2 * 60 * 60 * 1000L;

    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final JWTVerifier verifier;

    public OperatorController(OrderService orderService,
                              ProductRepository productRepository,
                              CustomerRepository customerRepository) {
        this.orderService = orderService;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.verifier = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build();
    }

    /** Reject the request with 401/403 if the bearer token is missing, invalid, or non-operator. */
    private void verifyOperatorToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }
        try {
            DecodedJWT jwt = verifier.verify(authHeader.substring(7));
            if (!"operator".equals(jwt.getClaim("role").asString())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied — operator role required");
            }
        } catch (JWTVerificationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid JWT token: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------

    /** POST /operator/login — exchange credentials for a 2-hour JWT. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (OPERATOR_USERNAME.equals(loginRequest.getUsername())
                && OPERATOR_PASSWORD.equals(loginRequest.getPassword())) {

            Date now = new Date();
            String token = JWT.create()
                    .withClaim("sub", loginRequest.getUsername())
                    .withClaim("role", "operator")
                    .withIssuedAt(now)
                    .withExpiresAt(new Date(now.getTime() + TOKEN_TTL_MS))
                    .sign(Algorithm.HMAC256(SECRET_KEY));

            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("username", loginRequest.getUsername());
            return ResponseEntity.ok(response);
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid username or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ------------------------------------------------------------
    // Order management
    // ------------------------------------------------------------

    /** GET /operator/orders — all orders with HATEOAS links. */
    @GetMapping("/orders")
    public ResponseEntity<CollectionModel<EntityModel<OrderResponse>>> getAllOrders(
            @RequestHeader("Authorization") String authHeader) {
        verifyOperatorToken(authHeader);

        List<EntityModel<OrderResponse>> orderModels = orderService.getAllOrders().stream()
                .map(order -> {
                    OrderResponse body = OrderResponse.from(order);
                    EntityModel<OrderResponse> model = EntityModel.of(body);
                    model.add(linkTo(methodOn(OperatorController.class)
                            .updateOrderStatus(authHeader, body.getId(), null)).withRel("update-status"));
                    model.add(linkTo(methodOn(OperatorController.class)
                            .getCustomerRevenue(authHeader, body.getCustomerId())).withRel("customer-revenue"));
                    return model;
                })
                .collect(Collectors.toList());

        CollectionModel<EntityModel<OrderResponse>> collection = CollectionModel.of(orderModels);
        collection.add(linkTo(methodOn(OperatorController.class).getAllOrders(authHeader)).withSelfRel());
        return ResponseEntity.ok(collection);
    }

    /** PUT /operator/orders/{id}/status — set SHIPPED or OUT_OF_STOCK. */
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody StatusUpdate statusUpdate) {
        verifyOperatorToken(authHeader);

        if (statusUpdate == null || statusUpdate.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing status in request body");
        }
        orderService.updateOrderStatus(id, statusUpdate.getStatus());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // Product management
    // ------------------------------------------------------------

    /** PUT /operator/products/{id}/price — update a product's retail price. */
    @PutMapping("/products/{id}/price")
    public ResponseEntity<Void> updateProductPrice(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody PriceUpdate priceUpdate) {
        verifyOperatorToken(authHeader);

        if (priceUpdate == null || priceUpdate.getRetailPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing retailPrice in request body");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        product.setRetailPrice(priceUpdate.getRetailPrice());
        productRepository.save(product);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // Customer analytics
    // ------------------------------------------------------------

    /** GET /operator/customers/{id}/revenue — total spend across non-cancelled orders. */
    @GetMapping("/customers/{id}/revenue")
    public ResponseEntity<RevenueResponse> getCustomerRevenue(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {
        verifyOperatorToken(authHeader);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        BigDecimal revenue = orderService.calculateCustomerRevenue(id);
        return ResponseEntity.ok(new RevenueResponse(id, customer.getName(), revenue));
    }

    // ------------------------------------------------------------
    // Request/response DTOs
    // ------------------------------------------------------------

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

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
