package com.example.demo.service;

import com.example.demo.model.Order;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Product;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/*
 * THis class handles creating/cancelling orders.
 * Checks stock with wholesaler before creating orders.
 */
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final WholesalerService wholesalerService;
    
    public OrderService(OrderRepository orderRepository,
                       ProductRepository productRepository,
                       CustomerRepository customerRepository,
                       WholesalerService wholesalerService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.wholesalerService = wholesalerService;
    }
    
    /**
     * Create a new order after validating customer, product, and stock availability.
     * Checks wholesaler stock before creating the order.
     */
    public Order createOrder(String customerId, String productId, int quantity) {
        
        // check customer exists
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Customer not found: " + customerId);
        }
        
        // check product exists
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Product not found: " + productId));
        
        // Check if wholesaler has stock
        if (!wholesalerService.hasStock(product.getWholesalerId(), quantity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Insufficient stock available from wholesaler");
        }
        
        // Check if order would be profitable
        if (!wholesalerService.isProfitable(product.getWholesalerId(), product.getRetailPrice())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Cannot create order - retail price too low to be profitable");
        }
        
        // Create and save order
        Order order = new Order(customerId, productId, quantity);
        return orderRepository.save(order);
    }
    
    //Get a single order by ID.
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Order not found: " + orderId));
    }
    
    //Get all orders for a specific customer.
    public List<Order> getOrdersByCustomer(String customerId) {
        // Validate customer exists
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Customer not found: " + customerId);
        }
        
        return orderRepository.findByCustomerId(customerId);
    }
    
    //Cancel an order. Only works if order status is pending
    public void cancelOrder(String orderId) {
        Order order = getOrder(orderId);
        
        // Checks if order can be cancelled
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Cannot cancel order - status is " + order.getStatus());
        }
        
        // Update status to cancelled
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
    
    //Get all orders in the system for operator
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    /*
     * Update order status (for operator).
     * Can set status to SHIPPED or OUT_OF_STOCK.
     */
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        order.setStatus(newStatus);
        orderRepository.save(order);
    }
    
    /**
     * Calculate total revenue from a customer.
     * Sums up retail prices of all non-cancelled orders.
     */
    public double calculateCustomerRevenue(String customerId) {
        // Validate customer exists
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Customer not found: " + customerId);
        }
        
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        
        double totalRevenue = 0.0;
        
        for (Order order : orders) {
            // Only count non-cancelled orders
            if (order.getStatus() != OrderStatus.CANCELLED) {
                Product product = productRepository.findById(order.getProductId()).orElse(null);
                if (product != null) {
                    totalRevenue += product.getRetailPrice() * order.getQuantity();
                }
            }
        }
        
        return totalRevenue;
    }
}