package com.joel.ordermanagement.wholesaler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Order-time stock and profitability checks, delegating HTTP + retries +
 * caching to {@link WholesalerClient}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WholesalerService {

    private final WholesalerClient client;

    /** True iff the wholesaler reports {@code in_stock >= quantity} for the product. */
    public boolean hasStock(String wholesalerId, int quantity) {
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
        Map<String, Object> product = client.getProduct(wholesalerId);
        if (product == null) return null;
        Object priceObj = product.get("price");
        return priceObj == null ? null : BigDecimal.valueOf(((Number) priceObj).doubleValue());
    }

    private static Integer asInteger(Object o) {
        return (o instanceof Number n) ? n.intValue() : null;
    }
}
