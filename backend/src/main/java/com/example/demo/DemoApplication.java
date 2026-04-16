package com.example.demo;

import com.example.demo.model.Customer;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.WholesalerSyncService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main class for the Order Management System.
 * this class initializes the database with customers and syncs products from wholesaler.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    /**
     * Initialize database with customers and sync products from wholesaler.
     * Uses all 3 wholesaler API endpoints to discover products!
     */
    @Bean
    public CommandLineRunner initData(CustomerRepository customerRepository,
                                      ProductRepository productRepository,
                                      WholesalerSyncService wholesalerSyncService) {
        return args -> {
            
            // Create 3 hardcoded customers as per assignment 
            customerRepository.save(new Customer(
                "CUST001",
                "Michelle James",
                "michellejames@gmail.com",
                "Ajman, UAE"
            ));
            
            customerRepository.save(new Customer(
                "CUST002",
                "Katelyn James",
                "kattyjames@gmail.com",
                "Sharjah, UAE"
            ));
            
            customerRepository.save(new Customer(
                "CUST003",
                "Steve Brown",
                "stevebrownn@gmail.com",
                "Dubai, UAE"
            ));
            
            System.out.println("✓ Customers initialized: " + customerRepository.count());
       
            //Sync only drills 
            wholesalerSyncService.syncDrillsOnly();
            System.out.println("✓ Products in database: " + productRepository.count());
        };
    }
}