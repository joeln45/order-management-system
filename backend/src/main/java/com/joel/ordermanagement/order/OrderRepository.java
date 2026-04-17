package com.joel.ordermanagement.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Spring Data JPA repository for {@link Order}. */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * Derived query that traverses the {@code customer} relationship.
     * Underscore tells Spring Data to navigate {@code order.customer.id} →
     * generated SQL: {@code WHERE customer_id = ?}.
     */
    List<Order> findByCustomer_Id(String customerId);
}
