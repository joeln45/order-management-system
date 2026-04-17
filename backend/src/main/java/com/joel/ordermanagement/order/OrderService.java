package com.joel.ordermanagement.order;

import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import com.joel.ordermanagement.wholesaler.WholesalerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business logic for orders: creation (with stock + profitability checks),
 * cancellation, status transitions, and customer revenue calculation.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final WholesalerService wholesalerService;

    /** Create a new order after validating customer, product, stock and profitability. */
    public Order createOrder(String customerId, String productId, int quantity) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found: " + customerId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + productId));

        if (!wholesalerService.hasStock(product.getWholesalerId(), quantity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient stock available from wholesaler");
        }

        if (!wholesalerService.isProfitable(product.getWholesalerId(), product.getRetailPrice())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot create order — retail price too low to be profitable");
        }

        return orderRepository.save(new Order(customer, product, quantity));
    }

    /** Get a single order or 404. */
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order not found: " + orderId));
    }

    /** All orders for a given customer (after validating the customer exists). */
    public List<Order> getOrdersByCustomer(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        }
        return orderRepository.findByCustomer_Id(customerId);
    }

    /** Cancel an order. Only permitted while still {@link OrderStatus#PENDING}. */
    public void cancelOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot cancel order — status is " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    /** All orders in the system (operator view). */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    /** Transition order to a new status (operator action). */
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    /**
     * Sum {@code retailPrice * quantity} across a customer's non-cancelled orders.
     * Phase 3 will switch this to use the order's price snapshot (per-line total).
     */
    public BigDecimal calculateCustomerRevenue(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        }

        return orderRepository.findByCustomer_Id(customerId).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .map(order -> order.getProduct().getRetailPrice()
                        .multiply(BigDecimal.valueOf(order.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
