package com.joel.ordermanagement.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Customer}.
 * Inherits findAll, findById, save, deleteById, existsById, count, etc.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    /** Resolve a customer by the username on its linked {@code User} row. */
    java.util.Optional<Customer> findByUser_Username(String username);
}
