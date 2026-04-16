package com.example.demo.repository;

import com.example.demo.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Customer entity.
 * Spring Data JPA automatically provides implementations for basic CRUD operations.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    
    /**
     * Spring automatically provides:
     * - findAll(): List all customers
     * - findById(id): Find customer by ID
     * - save(customer): Save or update customer
     * - deleteById(id): Delete customer by ID
     * - count(): Count total customers
     */
    
    // Custom query methods can be added here if needed
    // Example: Customer findByEmail(String email);
}