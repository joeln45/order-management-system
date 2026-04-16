package com.example.demo.repository;

import com.example.demo.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Product entity.
 * Spring Data JPA automatically provides implementations for basic CRUD operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    // Spring automatically provides:
    // - findAll()
    // - findById(id)
    // - save(product)
    // - deleteById(id)
    // - count()
    // etc.
    
    // You can add custom queries if needed, for example:
    // List<Product> findByDescriptionContaining(String keyword);
}