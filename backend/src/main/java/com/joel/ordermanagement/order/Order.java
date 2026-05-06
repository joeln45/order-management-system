package com.joel.ordermanagement.order;

import com.joel.ordermanagement.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer order: a header row that owns one or more {@link OrderItem}s.
 * <p>
 * The order itself carries the customer, status and date; the line items
 * carry the products, quantities and prices. {@link #total()} sums across
 * all items and is derived (not persisted) so it always matches the lines.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"customer", "items"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /**
     * EAGER + cascade ALL + orphanRemoval: saving an Order saves all its items,
     * deleting an Order deletes its items, and removing an item from this list
     * deletes it from the DB. EAGER keeps Phase 3 simple; Phase 7 will replace
     * it with a JOIN FETCH query to avoid the N+1 problem at scale.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Convenience constructor used when a customer places an order. */
    public Order(Customer customer) {
        this.customer = customer;
        this.status = OrderStatus.PENDING;
        this.orderDate = LocalDateTime.now();
    }

    /**
     * Attach a line item to this order and wire up both sides of the relationship.
     * Always use this rather than {@code items.add(...)} to keep the bidirectional
     * link consistent before {@code save()}.
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /** Sum of line totals across all items. Derived, never stored. */
    public BigDecimal total() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
