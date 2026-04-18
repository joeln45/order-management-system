package com.joel.ordermanagement.wholesaler;

import com.joel.ordermanagement.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Low-level wholesaler HTTP client.
 * <p>
 * Shared by {@link WholesalerService} (order-time stock/price checks) and
 * {@link WholesalerSyncService} (startup catalogue sync). Three resilience
 * layers are stacked on top of every call:
 * <ol>
 *   <li><b>Timeouts</b> — inherited from the shared {@link WebClient} bean.</li>
 *   <li><b>Retries with exponential backoff + jitter</b> — configurable per
 *       {@code wholesaler.retry.*} in {@code application.yml}. Only retries
 *       5xx / transport errors, never 4xx (those are real "not found"s).</li>
 *   <li><b>Caffeine cache</b> — 5-minute TTL on product fetches; see
 *       {@link CacheConfig}.</li>
 * </ol>
 */
@Slf4j
@Component
public class WholesalerClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public WholesalerClient(
            WebClient webClient,
            @Value("${wholesaler.retry.max-attempts}") int maxAttempts,
            @Value("${wholesaler.retry.initial-backoff}") Duration initialBackoff,
            @Value("${wholesaler.retry.max-backoff}") Duration maxBackoff) {
        this.webClient = webClient;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    /**
     * Fetch a product by its wholesaler id, cached for 5 minutes.
     * Returns {@code null} on 4xx (not found) or after all retries are exhausted.
     */
    @Cacheable(value = CacheConfig.WHOLESALER_PRODUCTS_CACHE, key = "#wholesalerId", unless = "#result == null")
    public Map<String, Object> getProduct(String wholesalerId) {
        return fetchMap("/product/" + wholesalerId);
    }

    /**
     * Fetch a category listing. Not cached — sync runs at most once at startup,
     * and the listing is small enough that caching buys nothing here.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getProductsInCategory(String category) {
        Map<String, Object> body = fetchMap("/category/" + category);
        if (body == null || !body.containsKey("_embedded")) return List.of();
        Map<String, Object> embedded = (Map<String, Object>) body.get("_embedded");
        Object products = embedded.get("products");
        return products instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
    }

    // ------------------------------------------------------------
    // Internal: GET with retry/backoff
    // ------------------------------------------------------------

    private Map<String, Object> fetchMap(String path) {
        try {
            return webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .retryWhen(retrySpec(path))
                    .block();
        } catch (WebClientResponseException e) {
            // 4xx → definitely not a retryable problem; log and move on.
            log.warn("Wholesaler returned {} for GET {}: {}", e.getStatusCode(), path, e.getStatusText());
            return null;
        } catch (Exception e) {
            log.warn("Wholesaler call failed after retries for GET {}: {}", path, e.getMessage());
            return null;
        }
    }

    private Retry retrySpec(String path) {
        return Retry.backoff(maxAttempts - 1L, initialBackoff)
                .maxBackoff(maxBackoff)
                .jitter(0.5)
                .filter(this::isRetryable)
                .doBeforeRetry(signal ->
                        log.info("Retrying wholesaler GET {} (attempt {}): {}",
                                path, signal.totalRetries() + 1, signal.failure().getMessage()));
    }

    /** Retry on network/timeouts and 5xx; never on 4xx. */
    private boolean isRetryable(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError();
        }
        return true;
    }
}
