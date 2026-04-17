package com.joel.ordermanagement.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Product}. */
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
}
