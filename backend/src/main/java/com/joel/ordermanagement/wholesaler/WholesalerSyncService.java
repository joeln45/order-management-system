package com.joel.ordermanagement.wholesaler;

import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bootstraps the local product catalogue from the wholesaler's stock service.
 *
 * <p>The wholesaler exposes a HATEOAS API with three discoverable endpoints:
 * <ol>
 *   <li>root — lists categories</li>
 *   <li>{@code /category/{name}} — lists products in a category</li>
 *   <li>{@code /product/{id}} — full details for a product</li>
 * </ol>
 *
 * <p>Today only the "drills" category is synced at startup, with a 30 % markup
 * applied to the wholesale price. The full multi-category sync will be revisited
 * in Phase 5 alongside caching, retries and a reactive client.
 */
@Service
@Slf4j
public class WholesalerSyncService {

    /** 30 % markup applied to wholesale price when computing our retail price. */
    private static final BigDecimal MARKUP_MULTIPLIER = new BigDecimal("1.30");

    @Value("${wholesaler.base-url}")
    private String wholesalerBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ProductRepository productRepository;

    public WholesalerSyncService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Sync only the "drills" category. Called once at application startup. */
    public void syncDrillsOnly() {
        log.info("Starting drills-only wholesaler sync...");
        int added = 0;

        for (Map<String, Object> productSummary : getProductsInCategory("drills")) {
            String wholesalerId = extractProductId(productSummary);
            if (wholesalerId == null || wholesalerId.isEmpty()) {
                log.warn("Could not extract product id from summary: {}", productSummary);
                continue;
            }
            log.debug("Processing wholesaler product {}", wholesalerId);
            Map<String, Object> details = getProductDetails(wholesalerId);
            if (details != null && addProductToDatabase(details)) {
                added++;
            }
        }
        log.info("Drills sync complete — {} new product(s) added", added);
    }

    // ------------------------------------------------------------
    // Wholesaler API calls
    // ------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getProductsInCategory(String category) {
        String url = wholesalerBaseUrl + "/category/" + category;
        log.debug("GET {}", url);
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("_embedded")) {
                return List.of();
            }
            Map<String, Object> embedded = (Map<String, Object>) response.get("_embedded");
            Object productsObj = embedded.get("products");
            return productsObj instanceof List<?>
                    ? (List<Map<String, Object>>) productsObj
                    : List.of();
        } catch (Exception e) {
            log.warn("Error fetching category '{}': {}", category, e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getProductDetails(String wholesalerId) {
        String url = wholesalerBaseUrl + "/product/" + wholesalerId;
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.warn("Error fetching product {}: {}", wholesalerId, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    /**
     * Pull the wholesaler product id from either the {@code id} field or
     * the {@code _links.self.href} URL (the wholesaler API has used both shapes).
     */
    @SuppressWarnings("unchecked")
    private String extractProductId(Map<String, Object> productSummary) {
        if (productSummary.containsKey("id")) {
            return (String) productSummary.get("id");
        }
        if (productSummary.containsKey("_links")) {
            Map<String, Object> links = (Map<String, Object>) productSummary.get("_links");
            if (links.containsKey("self")) {
                Map<String, Object> self = (Map<String, Object>) links.get("self");
                String href = (String) self.get("href");
                if (href != null && href.contains("/product/")) {
                    return href.substring(href.lastIndexOf("/") + 1);
                }
            }
        }
        return null;
    }

    /** Persist a wholesaler product into the local catalogue (idempotent on wholesalerId). */
    private boolean addProductToDatabase(Map<String, Object> productDetails) {
        String wholesalerId = (String) productDetails.get("id");
        String description = (String) productDetails.get("description");
        Object priceObj = productDetails.get("price");

        boolean alreadyPresent = productRepository.findAll().stream()
                .anyMatch(p -> p.getWholesalerId().equals(wholesalerId));
        if (alreadyPresent) {
            log.debug("Product {} already exists; skipping", wholesalerId);
            return false;
        }
        if (priceObj == null) {
            return false;
        }

        BigDecimal wholesalePrice = BigDecimal.valueOf(((Number) priceObj).doubleValue());
        BigDecimal retailPrice = wholesalePrice.multiply(MARKUP_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);

        productRepository.save(new Product(description, retailPrice, wholesalerId));
        log.info("Added '{}' (wholesale £{} → retail £{})", description, wholesalePrice, retailPrice);
        return true;
    }
}
