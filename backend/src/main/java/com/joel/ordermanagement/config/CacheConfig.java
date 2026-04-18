package com.joel.ordermanagement.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Local, in-JVM caching via Caffeine.
 * <p>
 * Scope: outbound wholesaler responses only. Catalogue prices barely change
 * minute-to-minute, and every {@code createOrder} call would otherwise hit
 * the wholesaler twice per line item (stock + profitability). Caching drops
 * this to once per 5-minute window per product — massive reduction in
 * wholesaler load and p99 latency.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String WHOLESALER_PRODUCTS_CACHE = "wholesaler-products";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(WHOLESALER_PRODUCTS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats());
        return manager;
    }
}
