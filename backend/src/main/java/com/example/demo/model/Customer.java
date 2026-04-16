package com.example.demo.model;

import jakarta.persistence.*;

/*
 * Customer entity - stores customer info (name, email, address).
 * Assignment says to hardcode 3 customers, so we do that in DemoApplication.
 */
@Entity
@Table(name = "customers")
public class Customer {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String postalAddress;
    
    
    //Default constructor for JPA.

    public Customer() {
    }
    
    // Constructor with all fields which makes it easier to create customers
    public Customer(String id, String name, String email, String postalAddress) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.postalAddress = postalAddress;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPostalAddress() {
        return postalAddress;
    }
    
    public void setPostalAddress(String postalAddress) {
        this.postalAddress = postalAddress;
    }
    
    @Override
    public String toString() {
        return "Customer{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", postalAddress='" + postalAddress + '\'' +
                '}';
    }
}