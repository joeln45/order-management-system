package com.joel.ordermanagement.order;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A customer order. The current schema is single-product per order;
 * Phase 3 promotes Order to a header that owns multiple {@code OrderItem}s.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    /** Convenience constructor used when a customer places an order. */
    public Order(String customerId, String productId, Integer quantity) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.orderDate = LocalDateTime.now();
    }
}
