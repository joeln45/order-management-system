package com.joel.ordermanagement.order;

import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.NotFoundException;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import com.joel.ordermanagement.wholesaler.WholesalerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business logic for orders: multi-item creation (with per-line stock +
 * profitability checks), cancellation, status transitions, and revenue
 * calculation from historical line-item prices.
 * <p>
 * Throws transport-agnostic domain exceptions ({@link NotFoundException},
 * {@link BusinessRuleException}); the HTTP status code translation happens
 * in {@link com.joel.ordermanagement.exception.GlobalExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final WholesalerService wholesalerService;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("Order must contain at least one item");
        }

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Customer", request.getCustomerId()));

        Order order = new Order(customer);

        for (CreateOrderRequest.LineItem line : request.getItems()) {
            if (line.getProductId() == null || line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new BusinessRuleException("Each item must have a productId and a positive quantity");
            }

            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> NotFoundException.of("Product", line.getProductId()));

            if (!wholesalerService.hasStock(product.getWholesalerId(), line.getQuantity())) {
                throw new BusinessRuleException(
                        "Insufficient stock for product: " + product.getDescription());
            }

            if (!wholesalerService.isProfitable(product.getWholesalerId(), product.getRetailPrice())) {
                throw new BusinessRuleException(
                        "Cannot sell " + product.getDescription() + ": retail price not profitable");
            }

            order.addItem(new OrderItem(product, line.getQuantity(), product.getRetailPrice()));
        }

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw NotFoundException.of("Customer", customerId);
        }
        return orderRepository.findByCustomer_Id(customerId);
    }

    @Transactional
    public void cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("Cannot cancel order: status is " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    /**
     * Total revenue from a customer = sum of line totals across all of their
     * non-cancelled orders, using the {@code priceAtPurchase} snapshot on each
     * line. Later product price edits do not retroactively change this figure.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateCustomerRevenue(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw NotFoundException.of("Customer", customerId);
        }

        return orderRepository.findByCustomer_Id(customerId).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .map(Order::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
