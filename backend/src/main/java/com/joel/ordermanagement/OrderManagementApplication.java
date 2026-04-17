package com.joel.ordermanagement;

import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.product.ProductRepository;
import com.joel.ordermanagement.wholesaler.WholesalerSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the Order Management System.
 * Seeds the database with the three demo customers and triggers the
 * initial wholesaler product sync on startup.
 */
@Slf4j
@SpringBootApplication
public class OrderManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderManagementApplication.class, args);
    }

    /**
     * One-time data initialisation at app startup.
     * Replaced by Flyway seed migrations in Phase 2.
     */
    @Bean
    public CommandLineRunner initData(CustomerRepository customerRepository,
                                      ProductRepository productRepository,
                                      WholesalerSyncService wholesalerSyncService) {
        return args -> {
            customerRepository.save(new Customer(
                    "CUST001", "Michelle James", "michellejames@gmail.com", "Ajman, UAE"));
            customerRepository.save(new Customer(
                    "CUST002", "Katelyn James", "kattyjames@gmail.com", "Sharjah, UAE"));
            customerRepository.save(new Customer(
                    "CUST003", "Steve Brown", "stevebrownn@gmail.com", "Dubai, UAE"));

            log.info("Customers initialised: {}", customerRepository.count());

            wholesalerSyncService.syncDrillsOnly();

            log.info("Products in database: {}", productRepository.count());
        };
    }
}
