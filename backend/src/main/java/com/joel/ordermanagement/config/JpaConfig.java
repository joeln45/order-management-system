package com.joel.ordermanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing across the application.
 * <p>
 * With this annotation in place, fields marked {@code @CreatedDate} and
 * {@code @LastModifiedDate} on entities (with {@code @EntityListeners(AuditingEntityListener.class)})
 * are populated automatically by Spring on insert and update.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
