package com.joel.ordermanagement.wholesaler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Read-only client for the wholesaler stock service: stock checks and
 * profitability checks during order creation. Replaced with a reactive
 * {@code WebClient} + Caffeine cache + retries in Phase 5.
 */
@Service
@Slf4j
public class WholesalerService {

    @Value("${wholesaler.base-url}")
    private String wholesalerBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** Fetch a product's full record (description, price, in_stock, ...) from the wholesaler. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getWholesalerProduct(String wholesalerId) {
        try {
            String url = wholesalerBaseUrl + "/product/" + wholesalerId;
            return restTemplate.getForObject(url, Map.class);
        } catch (RestClientException e) {
            log.warn("Failed to fetch wholesaler product {}: {}", wholesalerId, e.getMessage());
            return null;
        }
    }

    /** True iff the wholesaler reports {@code in_stock >= quantity} for the product. */
    public boolean hasStock(String wholesalerId, int quantity) {
        Map<String, Object> product = getWholesalerProduct(wholesalerId);
        if (product == null) {
            return false;
        }
        Integer inStock = (Integer) product.get("in_stock");
        return inStock != null && inStock >= quantity;
    }

    /** True iff retail price strictly exceeds the wholesaler's current price. */
    public boolean isProfitable(String wholesalerId, double retailPrice) {
        Map<String, Object> product = getWholesalerProduct(wholesalerId);
        if (product == null) {
            return false;
        }
        Double wholesalePrice = (Double) product.get("price");
        return wholesalePrice != null && retailPrice > wholesalePrice;
    }

    /** Current wholesale price for a product, or {@code null} if unknown. */
    public Double getWholesalePrice(String wholesalerId) {
        Map<String, Object> product = getWholesalerProduct(wholesalerId);
        return product == null ? null : (Double) product.get("price");
    }
}
