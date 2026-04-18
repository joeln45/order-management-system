package com.joel.ordermanagement.auth;

import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default {@code OPERATOR} user and a demo {@code CUSTOMER} user
 * on startup, using {@link PasswordEncoder} to hash passwords — something
 * a raw Flyway SQL migration can't do. Guarded by {@code app.seed.enabled}
 * so production can leave it off.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthSeeder {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:false}")
    private boolean enabled;

    @Value("${app.seed.operator-username}")
    private String operatorUsername;

    @Value("${app.seed.operator-password}")
    private String operatorPassword;

    @Value("${app.seed.demo-customer-username}")
    private String demoCustomerUsername;

    @Value("${app.seed.demo-customer-password}")
    private String demoCustomerPassword;

    @Bean
    public ApplicationRunner seedUsers() {
        return args -> {
            if (!enabled) {
                log.info("AuthSeeder disabled (app.seed.enabled=false)");
                return;
            }
            seedOperator();
            seedDemoCustomer();
        };
    }

    @Transactional
    void seedOperator() {
        if (userRepository.existsByUsername(operatorUsername)) return;

        userRepository.save(new User(
                operatorUsername,
                passwordEncoder.encode(operatorPassword),
                Role.OPERATOR));
        log.info("Seeded OPERATOR user: {}", operatorUsername);
    }

    @Transactional
    void seedDemoCustomer() {
        if (userRepository.existsByUsername(demoCustomerUsername)) return;

        User user = userRepository.save(new User(
                demoCustomerUsername,
                passwordEncoder.encode(demoCustomerPassword),
                Role.CUSTOMER));

        Customer customer = new Customer();
        customer.setId("CUST-DEMO");
        customer.setName("Demo Customer");
        customer.setEmail("demo@example.com");
        customer.setPostalAddress("1 Demo Lane, Stirling FK9 4LA");
        customer.setUser(user);
        customerRepository.save(customer);

        log.info("Seeded demo CUSTOMER user: {} (customerId={})", demoCustomerUsername, customer.getId());
    }
}
