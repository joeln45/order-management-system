package com.example.demo.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.example.demo.model.Customer;
import com.example.demo.model.Order;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Product;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.OrderService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

/*
 * Operator controller needs JWT auth for all endpoints.
 * Handles order management, price updates, customer revenue.
 */
@RestController
@CrossOrigin
@RequestMapping("/operator")
public class OperatorController {
    
	// JWT secret - needs to be 32+ chars for HMAC256
    private static final String SECRET_KEY = "order-management-secret-key-must-be-at-least-32-characters-long";
    
    // Hardcoded credentials for demo
    private static final String OPERATOR_USERNAME = "operator";
    private static final String OPERATOR_PASSWORD = "password123";
    
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
        
        // Initialize JWT verifier
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        this.verifier = JWT.require(algorithm).build();
    }
    
    /**
     * Verify JWT token from Authorization header.
     * Throws 401 if missing or invalid.
     */
    private void verifyOperatorToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, 
                "Missing or invalid Authorization header");
        }
        
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        
        try {
            DecodedJWT jwt = verifier.verify(token);
            
            // Check if role is operator
            String role = jwt.getClaim("role").asString();
            if (!"operator".equals(role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Access denied - operator role required");
            }
            
        } catch (JWTVerificationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, 
                "Invalid JWT token: " + e.getMessage());
        }
    }
    
    // I have used AI for helping me implmenting JWT Token generation, verification and authentication
    // Auth endpoints
    
    /**
     * Login endpoint - validates credentials and returns JWT token.
     * This is the only way to get a token (secure authentication).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        
        // Validate credentials
        if (OPERATOR_USERNAME.equals(username) && OPERATOR_PASSWORD.equals(password)) {
            // Generate JWT token
            Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
            
            // Get current time
            Date now = new Date();
            Date expiry = new Date(now.getTime() + 7200000); // 2 hours 
            
            String token = JWT.create()
                .withClaim("sub", username)
                .withClaim("role", "operator")
                .withIssuedAt(now)
                .withExpiresAt(expiry)
                .sign(algorithm);
            
            // Return token
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("username", username);
            
            return ResponseEntity.ok(response);
        }
        
        // Invalid credentials
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid username or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    // Order management 
    
    /**
     * View all orders in the system.
     * Requires JWT authentication.
     * Returns orders with HATEOAS links.
     */
    @GetMapping("/orders")
    public ResponseEntity<CollectionModel<EntityModel<Order>>> getAllOrders(
            @RequestHeader("Authorization") String authHeader) {
        
        verifyOperatorToken(authHeader);
        
        List<Order> orders = orderService.getAllOrders();
        
        // Add HATEOAS links to orders
        List<EntityModel<Order>> orderModels = orders.stream()
            .map(order -> {
                EntityModel<Order> model = EntityModel.of(order);
                
                // Self link
                model.add(linkTo(methodOn(OperatorController.class)
                    .getAllOrders(authHeader))
                    .withSelfRel());
                
                // Link to update status
                model.add(linkTo(methodOn(OperatorController.class)
                    .updateOrderStatus(authHeader, order.getId(), null))
                    .withRel("update-status"));
                
                // Link to customer revenue
                model.add(linkTo(methodOn(OperatorController.class)
                    .getCustomerRevenue(authHeader, order.getCustomerId()))
                    .withRel("customer-revenue"));
                
                return model;
            })
            .collect(Collectors.toList());
        
        CollectionModel<EntityModel<Order>> collection = CollectionModel.of(orderModels);
        
        // Self link
        collection.add(linkTo(methodOn(OperatorController.class)
            .getAllOrders(authHeader))
            .withSelfRel());
        
        return ResponseEntity.ok(collection);
    }
    
    /*
     * Update order status.
     * Operator can change status to SHIPPED or OUT_OF_STOCK.
     */
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody StatusUpdate statusUpdate) {
        
        verifyOperatorToken(authHeader);
        
        if (statusUpdate == null || statusUpdate.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Missing status in request body");
        }
        
        orderService.updateOrderStatus(id, statusUpdate.getStatus());
        
        return ResponseEntity.noContent().build();
    }
    
    
    // Product management
    
    /**
     * Update product's orginal price.
     */
    @PutMapping("/products/{id}/price")
    public ResponseEntity<Void> updateProductPrice(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody PriceUpdate priceUpdate) {
        
        verifyOperatorToken(authHeader);
        
        if (priceUpdate == null || priceUpdate.getRetailPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Missing retailPrice in request body");
        }
        
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Product not found"));
        
        product.setRetailPrice(priceUpdate.getRetailPrice());
        productRepository.save(product);
        
        return ResponseEntity.noContent().build();
    }
    
    
    //Customer analytics
    
    /*
     * View total revenue from a specific customer.
     */
    @GetMapping("/customers/{id}/revenue")
    public ResponseEntity<RevenueResponse> getCustomerRevenue(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {
        
        verifyOperatorToken(authHeader);
        
        // Verify customer exists
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Customer not found"));
        
        double revenue = orderService.calculateCustomerRevenue(id);
        
        RevenueResponse response = new RevenueResponse(id, customer.getName(), revenue);
        
        return ResponseEntity.ok(response);
    }
    
    
    // Request/response classes
    
    // Login request
    public static class LoginRequest {
        private String username;
        private String password;
        
        public LoginRequest() {}
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
    
    // Request body for updating order status.
    public static class StatusUpdate {
        private OrderStatus status;
        
        public StatusUpdate() {}
        
        public OrderStatus getStatus() {
            return status;
        }
        
        public void setStatus(OrderStatus status) {
            this.status = status;
        }
    }
    
    //Request body for updating product price.
    public static class PriceUpdate {
        private Double retailPrice;
        
        public PriceUpdate() {}
        
        public Double getRetailPrice() {
            return retailPrice;
        }
        
        public void setRetailPrice(Double retailPrice) {
            this.retailPrice = retailPrice;
        }
    }
    
    //Response for customer revenue endpoint.
    public static class RevenueResponse {
        private String customerId;
        private String customerName;
        private double totalRevenue;
        
        public RevenueResponse(String customerId, String customerName, double totalRevenue) {
            this.customerId = customerId;
            this.customerName = customerName;
            this.totalRevenue = totalRevenue;
        }
        
        public String getCustomerId() {
            return customerId;
        }
        
        public String getCustomerName() {
            return customerName;
        }
        
        public double getTotalRevenue() {
            return totalRevenue;
        }
    }
}