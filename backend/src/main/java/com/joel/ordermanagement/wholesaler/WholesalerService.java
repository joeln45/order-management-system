package com.joel.ordermanagement.wholesaler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Order-time stock and profitability checks, delegating HTTP + retries +
 * caching to {@link WholesalerClient}.
 * <p>
 * Products with a {@code demo-*} wholesaler id use local fallback data
 * so the demo works when the external wholesaler API is unavailable.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WholesalerService {

    /** Markup we applied when seeding demo products (retail = wholesale × 1.30). */
    private static final BigDecimal DEMO_MARKUP = new BigDecimal("1.30");
    /** Simulated stock level for every demo product. */
    private static final int DEMO_IN_STOCK = 50;

    private final WholesalerClient client;

    /** True iff the wholesaler reports {@code in_stock >= quantity} for the product. */
    public boolean hasStock(String wholesalerId, int quantity) {
        if (isDemoProduct(wholesalerId)) return DEMO_IN_STOCK >= quantity;
        Map<String, Object> product = client.getProduct(wholesalerId);
        if (product == null) return false;
        Integer inStock = asInteger(product.get("in_stock"));
        return inStock != null && inStock >= quantity;
    }

    /** True iff retail price strictly exceeds the wholesaler's current price. */
    public boolean isProfitable(String wholesalerId, BigDecimal retailPrice) {
        BigDecimal wholesalePrice = getWholesalePrice(wholesalerId);
        return wholesalePrice != null && retailPrice.compareTo(wholesalePrice) > 0;
    }

    /** Current wholesale price for a product, or {@code null} if unknown. */
    public BigDecimal getWholesalePrice(String wholesalerId) {
        if (isDemoProduct(wholesalerId)) {
            // Back-calculate from the known retail price seed so profitability always holds.
            // We don't have the retail price here, so return a non-null sentinel that the
            // caller (OrderService) will compare against retailPrice; returning a very small
            // value ensures retail > wholesale (i.e. profitable) for any seeded price.
            return new BigDecimal("0.01");
        }
        Map<String, Object> product = client.getProduct(wholesalerId);
        if (product == null) return null;
        Object priceObj = product.get("price");
        return priceObj == null ? null : BigDecimal.valueOf(((Number) priceObj).doubleValue());
    }

    /** Demo product ids are prefixed with {@code demo-} and never go to the external API. */
    private static boolean isDemoProduct(String wholesalerId) {
        return wholesalerId != null && wholesalerId.startsWith("demo-");
    }

    private static Integer asInteger(Object o) {
        return (o instanceof Number n) ? n.intValue() : null;
    }
}
