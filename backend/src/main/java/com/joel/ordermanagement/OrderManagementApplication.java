package com.joel.ordermanagement;

import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.product.ProductRepository;
import com.joel.ordermanagement.wholesaler.WholesalerSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Order Management System.
 * <p>
 * Customer seed data is owned by Flyway migration {@code V2__seed_customers.sql}.
 * The {@link CommandLineRunner} below only triggers the wholesaler product sync,
 * a runtime concern that doesn't belong in a database migration.
 */
@Slf4j
@SpringBootApplication
public class OrderManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderManagementApplication.class, args);
    }

    @Bean
    @Profile("!test")
    public CommandLineRunner initData(CustomerRepository customerRepository,
                                      ProductRepository productRepository,
                                      WholesalerSyncService wholesalerSyncService) {
        return args -> {
            log.info("Customers in database (loaded by Flyway): {}", customerRepository.count());
            wholesalerSyncService.syncDrillsOnly();
            log.info("Products in database: {}", productRepository.count());
        };
    }
}
