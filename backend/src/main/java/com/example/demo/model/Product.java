package com.example.demo.model;

import jakarta.persistence.*;

/*
 * Product entity for items we sell.
 * Links to wholesaler products via wholesalerId field.
 */
@Entity
@Table(name = "products")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String description;
    
    @Column(nullable = false)
    private Double retailPrice;  // Price in pounds
    
    @Column(nullable = false)
    private String wholesalerId;  // ID in wholesaler's system
    
    // Default constructor
    public Product() {
    }
    
    // Constructor 
    public Product(String description, Double retailPrice, String wholesalerId) {
        this.description = description;
        this.retailPrice = retailPrice;
        this.wholesalerId = wholesalerId;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Double getRetailPrice() {
        return retailPrice;
    }
    
    public void setRetailPrice(Double retailPrice) {
        this.retailPrice = retailPrice;
    }
    
    public String getWholesalerId() {
        return wholesalerId;
    }
    
    public void setWholesalerId(String wholesalerId) {
        this.wholesalerId = wholesalerId;
    }
    
    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", description='" + description + '\'' +
                ", retailPrice=" + retailPrice +
                ", wholesalerId='" + wholesalerId + '\'' +
                '}';
    }
}