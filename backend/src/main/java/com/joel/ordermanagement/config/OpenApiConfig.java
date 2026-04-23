package com.joel.ordermanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 *
 * <p>springdoc auto-discovers endpoints, request bodies, and response shapes
 * from our controllers. This bean only supplies the metadata that can't be
 * inferred from code: branding, contact info, server URL, and — most
 * importantly — the JWT bearer-token security scheme so the Swagger UI
 * "Authorize" button can attach {@code Authorization: Bearer ...} to
 * "Try it out" calls.
 *
 * <p>Browse the live docs at:
 * <ul>
 *   <li>{@code http://localhost:8080/swagger-ui.html} — interactive UI</li>
 *   <li>{@code http://localhost:8080/v3/api-docs}     — raw JSON spec</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** Name used to cross-reference the security scheme on operations. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI orderManagementOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local dev")
                ))
                // Global default: every endpoint requires a bearer token in the UI.
                // Public endpoints opt out with @SecurityRequirements({}) on the method.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, bearerJwtScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("Order Management System API")
                .version("v1")
                .description("""
                        RESTful backend for a drop-shipping retailer. Customers browse products,
                        place multi-item orders, and authenticate via JWT. Operators manage the
                        catalogue and fulfil orders. Products are sourced from an external
                        wholesaler stock service with a 30% markup.

                        **Authentication:** `POST /auth/login` returns an access token (15 min)
                        and a refresh token (7 days). Click **Authorize** below and paste the
                        access token to try protected endpoints.
                        """)
                .contact(new Contact()
                        .name("Joel N.")
                        .url("https://github.com/joeln45"))
                .license(new License()
                        .name("MIT")
                        .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * HTTP bearer scheme with JWT format. Swagger UI renders this as an
     * "Authorize" modal that accepts a raw JWT and injects it as
     * {@code Authorization: Bearer <token>} on subsequent requests.
     */
    private SecurityScheme bearerJwtScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the access token returned by POST /auth/login");
    }
}
