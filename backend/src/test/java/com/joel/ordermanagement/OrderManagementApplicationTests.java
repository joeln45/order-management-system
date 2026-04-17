package com.joel.ordermanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: starts the full Spring context to catch wiring issues.
 * Real test coverage (services, controllers, integration) lands in Phase 7.
 */
@SpringBootTest
class OrderManagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
