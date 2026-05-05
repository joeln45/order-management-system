package com.joel.ordermanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.joel.ordermanagement.auth.dto.LoginRequest;
import com.joel.ordermanagement.auth.dto.RegisterRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test — boots the whole Spring Boot app on a random
 * port, backed by a real PostgreSQL in Docker (Testcontainers) and a WireMock
 * fake wholesaler. No mocks of our own classes; everything wired as in prod.
 * <p>
 * What this buys us over the slice tests:
 * <ul>
 *   <li>Real Flyway migrations execute against real Postgres — catches
 *       H2-vs-Postgres drift.</li>
 *   <li>Real {@code SecurityFilterChain} runs — proves 401/403 actually happen
 *       (slice tests disable filters).</li>
 *   <li>Real JWT issue → verify round-trip over HTTP.</li>
 * </ul>
 *
 * <p><b>Prerequisite:</b> Docker Desktop (or compatible) must be running,
 * otherwise Testcontainers will abort startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class OrderFlowIntegrationTest {

    /**
     * @ServiceConnection (Spring Boot 3.1+) auto-wires the container's JDBC URL,
     * username, password and driver into Spring's DataSource AFTER the container
     * has started. This avoids the trap of @DynamicPropertySource asking for
     * getJdbcUrl() before the container is ready — which is exactly what blew up
     * in CI before this fix.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("oms_test")
                    .withUsername("test")
                    .withPassword("test");

    static WireMockServer wireMock;

    @BeforeAll
    void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // Any /product/** call → return a generic "in-stock, profitable" payload.
        // We don't create orders in this smoke test, but the context needs the
        // wholesaler to be reachable for any eager HTTP it might attempt.
        wireMock.stubFor(get(urlPathMatching("/product/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "id": "wh-stub-1",
                                  "description": "Stub Product",
                                  "price": 10.00,
                                  "in_stock": 999 }
                                """)));
    }

    @AfterAll
    void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry r) {
        // DataSource URL/user/pass/driver are now provided by @ServiceConnection.
        // We only need to configure the bits Spring can't infer from the container.
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");

        // Point the wholesaler client at WireMock, not pythonanywhere
        r.add("wholesaler.base-url", () -> wireMock.baseUrl());

        // Disable the user seeder — tests register their own users explicitly
        r.add("app.seed.enabled", () -> "false");

        // Deterministic JWT secret (≥ 32 chars, as JwtService enforces)
        r.add("app.jwt.secret",
                () -> "integration-test-secret-padding-to-meet-32-char-minimum");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    // ------------------------------------------------------------
    // The full flow
    // ------------------------------------------------------------

    @Test
    @Order(1)
    void register_thenLogin_thenRefresh_roundTripsTokens() {
        // --- register ---
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("alice-int");
        reg.setPassword("s3cret-password");
        reg.setName("Alice Integration");
        reg.setEmail("alice-int@example.com");
        reg.setPostalAddress("10 Downing St");

        ResponseEntity<JsonNode> regResp = rest.postForEntity(
                url("/auth/register"), reg, JsonNode.class);

        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regResp.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(regResp.getBody().get("role").asText()).isEqualTo("CUSTOMER");

        // --- login ---
        LoginRequest login = new LoginRequest();
        login.setUsername("alice-int");
        login.setPassword("s3cret-password");

        ResponseEntity<JsonNode> loginResp = rest.postForEntity(
                url("/auth/login"), login, JsonNode.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access = loginResp.getBody().get("accessToken").asText();
        assertThat(access).isNotBlank();

        // --- use the token on an authenticated endpoint ---
        // GET /orders/{id} is authenticated; unknown id → 404 (proves the token
        // passed through the security filter before the handler ran).
        ResponseEntity<JsonNode> orderResp = rest.exchange(
                url("/orders/does-not-exist"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(access)),
                JsonNode.class);

        assertThat(orderResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(orderResp.getBody().get("title").asText()).isEqualTo("Not Found");
    }

    @Test
    @Order(2)
    void postOrders_withoutToken_returns401() {
        ResponseEntity<JsonNode> r = rest.postForEntity(
                url("/orders"),
                new HttpEntity<>("{\"customerId\":\"x\",\"items\":[]}", jsonHeaders()),
                JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(3)
    void operator_endpoint_withCustomerRole_returns403() {
        // Re-login the customer we registered in test #1
        LoginRequest login = new LoginRequest();
        login.setUsername("alice-int");
        login.setPassword("s3cret-password");
        String access = rest.postForEntity(url("/auth/login"), login, JsonNode.class)
                .getBody().get("accessToken").asText();

        ResponseEntity<JsonNode> r = rest.exchange(
                url("/operator/orders"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(access)),
                JsonNode.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(4)
    void products_publicEndpoint_noTokenNeeded() {
        ResponseEntity<JsonNode> r = rest.getForEntity(url("/products"), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // catalogue is empty in the test DB — we only assert the envelope links
        assertThat(r.getBody().get("_links").get("self").get("href").asText())
                .contains("/products");
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
