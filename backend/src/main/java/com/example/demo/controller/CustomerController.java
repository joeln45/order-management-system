package com.example.demo.controller;

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

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

// I have used AI to help me in implementing HATEOAS links using Spring HATEOAS library with EntityModel and CollectionModel
/*
 * Customer controller -> handles products, orders, viewing order history.
 * All responses include HATEOAS links for navigation.
 */
@RestController  //handles http request and returns json response
@CrossOrigin   // resourse sharing
public class CustomerController {
    
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    
    public CustomerController(ProductRepository productRepository,
                            CustomerRepository customerRepository,
                            OrderService orderService) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderService = orderService;
    }
    
    //Product endpoints
    
    /*
     * GET /products - list all products
     * Each product has link to its details
     */
    @GetMapping("/products")
    public ResponseEntity<CollectionModel<EntityModel<Product>>> getAllProducts() {
        
        List<Product> products = productRepository.findAll();
        
        // Wrap products with HATEOAS links
        List<EntityModel<Product>> productModels = products.stream()
            .map(product -> {
                EntityModel<Product> model = EntityModel.of(product);
                
                // Add self link to each product
                model.add(linkTo(methodOn(CustomerController.class)
                    .getProduct(product.getId()))
                    .withSelfRel());
                
                return model;
            })
            .collect(Collectors.toList());
        
        // Add link to this products list
        CollectionModel<EntityModel<Product>> collection = CollectionModel.of(productModels);
        collection.add(linkTo(methodOn(CustomerController.class)
            .getAllProducts())
            .withSelfRel());
        
        return ResponseEntity.ok(collection);
    }
    
    /**
     * Get a single product by ID.
     * Response includes links to all products and to create an order.
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<EntityModel<Product>> getProduct(@PathVariable String id) {
        
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Product not found"));
        
        EntityModel<Product> model = EntityModel.of(product);
        
        // Self link
        model.add(linkTo(methodOn(CustomerController.class)
            .getProduct(id))
            .withSelfRel());
        
        // Link to all products
        model.add(linkTo(methodOn(CustomerController.class)
            .getAllProducts())
            .withRel("all-products"));
        
        // Link to create order (customers can order this product)
        model.add(linkTo(methodOn(CustomerController.class)
            .createOrder(null))
            .withRel("create-order"));
        
        return ResponseEntity.ok(model);
    }
    
    // Order endpoints
    
    /**
     * Create new order.
     * Request body should contain customerId, productId, and quantity.
     * Checks stock availability before creating order.
     */
    @PostMapping("/orders")
    public ResponseEntity<EntityModel<Order>> createOrder(@RequestBody OrderRequest request) {//json converted to object
        
        // Validate request
        if (request == null || request.getCustomerId() == null || 
            request.getProductId() == null || request.getQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Missing required fields: customerId, productId, quantity");
        }
        
        // Service checks stock and creates order
        Order order = orderService.createOrder(
            request.getCustomerId(), 
            request.getProductId(), 
            request.getQuantity()
        );
        
        EntityModel<Order> model = EntityModel.of(order);
        
        // Self link
        model.add(linkTo(methodOn(CustomerController.class)
            .getOrder(order.getId()))
            .withSelfRel());
        
        // Link to customer's orders
        model.add(linkTo(methodOn(CustomerController.class)
            .getCustomerOrders(order.getCustomerId()))
            .withRel("customer-orders"));
        
        // Link to product
        model.add(linkTo(methodOn(CustomerController.class)
            .getProduct(order.getProductId()))
            .withRel("product"));
        
        // Link to cancel 
        model.add(linkTo(methodOn(CustomerController.class)
            .cancelOrder(order.getId()))
            .withRel("cancel"));
        
        return ResponseEntity.status(HttpStatus.CREATED).body(model);
    }
    
    /**
     * Get a single order by ID.
     * Cancel link only shows if order is still PENDING
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<EntityModel<Order>> getOrder(@PathVariable String id) {
        
        Order order = orderService.getOrder(id);
        
        EntityModel<Order> model = EntityModel.of(order);
        
        // Self link
        model.add(linkTo(methodOn(CustomerController.class)
            .getOrder(id))
            .withSelfRel());
        
        // Link to product
        model.add(linkTo(methodOn(CustomerController.class)
            .getProduct(order.getProductId()))
            .withRel("product"));
        
        // Link to customer
        model.add(linkTo(methodOn(CustomerController.class)
            .getCustomer(order.getCustomerId()))
            .withRel("customer"));
        
        // Link to customer's all orders
        model.add(linkTo(methodOn(CustomerController.class)
            .getCustomerOrders(order.getCustomerId()))
            .withRel("customer-orders"));
        
        // Conditional link: only show cancel link if order is PENDING
        if (order.getStatus() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(CustomerController.class)
                .cancelOrder(id))
                .withRel("cancel"));
        }
        
        return ResponseEntity.ok(model);
    }
   
    /**
     * Get all orders for the customer.
     * List all orders for a customer
     */
    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<CollectionModel<EntityModel<Order>>> getCustomerOrders(
            @PathVariable String customerId) {
        
        List<Order> orders = orderService.getOrdersByCustomer(customerId);
        
        // Add links to each order
        List<EntityModel<Order>> orderModels = orders.stream()
            .map(order -> {
                EntityModel<Order> model = EntityModel.of(order);
                
                // Self link
                model.add(linkTo(methodOn(CustomerController.class)
                    .getOrder(order.getId()))
                    .withSelfRel());
                
                // Link to product
                model.add(linkTo(methodOn(CustomerController.class)
                    .getProduct(order.getProductId()))
                    .withRel("product"));
                
                // Cancel link if pending
                if (order.getStatus() == OrderStatus.PENDING) {
                    model.add(linkTo(methodOn(CustomerController.class)
                        .cancelOrder(order.getId()))
                        .withRel("cancel"));
                }
                
                return model;
            })
            .collect(Collectors.toList());
        
        // Add links to the collection
        CollectionModel<EntityModel<Order>> collection = CollectionModel.of(orderModels);
        
        // Self link
        collection.add(linkTo(methodOn(CustomerController.class)
            .getCustomerOrders(customerId))
            .withSelfRel());
        
        // Link to customer info
        collection.add(linkTo(methodOn(CustomerController.class)
            .getCustomer(customerId))
            .withRel("customer"));
        
        return ResponseEntity.ok(collection);
    }
    
    /*
     * Cancel an order.
     * Only works if order status is PENDING.
     */
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        
        orderService.cancelOrder(id);
        
        return ResponseEntity.noContent().build();
    }
    
    
    // Customer info endpoints
    
    /**
     * Get customer information by ID.
     * Links to their orders and product catalog
     */
    @GetMapping("/customers/{id}")
    public ResponseEntity<EntityModel<Customer>> getCustomer(@PathVariable String id) {
        
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Customer not found"));
        
        EntityModel<Customer> model = EntityModel.of(customer);
        
        // Self link
        model.add(linkTo(methodOn(CustomerController.class)
            .getCustomer(id))
            .withSelfRel());
        
        // Link to customer's orders
        model.add(linkTo(methodOn(CustomerController.class)
            .getCustomerOrders(id))
            .withRel("orders"));
        
        // Link to browse products
        model.add(linkTo(methodOn(CustomerController.class)
            .getAllProducts())
            .withRel("products"));
        
        return ResponseEntity.ok(model);
    }
    
    
    // Request body class
    
    /**
     * Capture order creation request data.
     * Contains customerId, productId, and quantity.
     */
    public static class OrderRequest {
        private String customerId;
        private String productId;
        private Integer quantity;
        
        // Default constructor 
        public OrderRequest() {}
        
        // Getters and setters
        public String getCustomerId() {
            return customerId;
        }
        
        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
        
        public String getProductId() {
            return productId;
        }
        
        public void setProductId(String productId) {
            this.productId = productId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}