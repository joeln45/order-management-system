package com.joel.ordermanagement.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Spring Data JPA repository for {@link Order}. */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    /** Derived query: Spring auto-implements {@code WHERE customer_id = ?}. */
    List<Order> findByCustomerId(String customerId);
}
