package com.joel.ordermanagement.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A product on sale by the drop-shipping retailer.
 * Linked to its source via {@code wholesalerId}; retail price is set by the operator.
 */
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String description;

    /** Price shown to customers, in GBP. */
    @Column(nullable = false)
    private Double retailPrice;

    /** Foreign reference to the wholesaler's stock service product ID. */
    @Column(nullable = false)
    private String wholesalerId;

    /** Convenience constructor used during sync (id is auto-generated). */
    public Product(String description, Double retailPrice, String wholesalerId) {
        this.description = description;
        this.retailPrice = retailPrice;
        this.wholesalerId = wholesalerId;
    }
}
