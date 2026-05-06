package com.joel.ordermanagement.wholesaler;

import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Pulls the drills catalogue from the wholesaler at startup and copies it
 * into our products table with a 30% markup on the wholesale price. Only
 * the drills category is synced; that's all the assignment asks for.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WholesalerSyncService {

    /** 30% markup over wholesale price. */
    private static final BigDecimal MARKUP_MULTIPLIER = new BigDecimal("1.30");

    private final WholesalerClient client;
    private final ProductRepository productRepository;

    /** Sync only the "drills" category. Called once at application startup. */
    public void syncDrillsOnly() {
        log.info("Starting drills-only wholesaler sync...");
        int added = 0;

        for (Map<String, Object> productSummary : client.getProductsInCategory("drills")) {
            String wholesalerId = extractProductId(productSummary);
            if (wholesalerId == null || wholesalerId.isEmpty()) {
                log.warn("Could not extract product id from summary: {}", productSummary);
                continue;
            }
            Map<String, Object> details = client.getProduct(wholesalerId);
            if (details != null && addProductToDatabase(details)) {
                added++;
            }
        }
        log.info("Drills sync complete: {} new product(s) added", added);
    }

    /**
     * Pull the wholesaler product id from either the id field or the
     * _links.self.href URL. The wholesaler API has used both shapes.
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
        log.info("Added '{}' (wholesale £{}, retail £{})", description, wholesalePrice, retailPrice);
        return true;
    }
}
