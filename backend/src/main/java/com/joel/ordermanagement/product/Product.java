package com.joel.ordermanagement.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A product on sale by the drop-shipping retailer.
 * Linked to its source via {@code wholesalerId}; retail price is set by the operator.
 */
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String description;

    /** Price shown to customers, in GBP. {@code BigDecimal} for exact decimal arithmetic. */
    @Column(name = "retail_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal retailPrice;

    /** Foreign reference to the wholesaler's stock service product ID. */
    @Column(name = "wholesaler_id", nullable = false)
    private String wholesalerId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Convenience constructor used during sync (id is auto-generated). */
    public Product(String description, BigDecimal retailPrice, String wholesalerId) {
        this.description = description;
        this.retailPrice = retailPrice;
        this.wholesalerId = wholesalerId;
    }
}
