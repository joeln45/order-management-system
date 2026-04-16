package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/*
 * Talks to wholesaler API to check stock and prices.
 * API: https://pmaier.eu.pythonanywhere.com/wss
 */
@Service
public class WholesalerService {
    
    private static final String WHOLESALER_BASE_URL = "https://pmaier.eu.pythonanywhere.com/wss";
    
    private final RestTemplate restTemplate;
    
    public WholesalerService() {
        this.restTemplate = new RestTemplate();
    }
    
    /*
     * Fetch product details from the wholesaler API.
     * Returns product info, stock level and wholesale price.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getWholesalerProduct(String wholesalerId) {
        try {
            String url = WHOLESALER_BASE_URL + "/product/" + wholesalerId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response;
        } catch (RestClientException e) {
            // Wholesaler API might be down or product doesn't exist
            System.out.println("Failed to fetch wholesaler product: " + wholesalerId);
            return null;
        }
    }
    
    //Check if wholesaler has sufficient stock for an order.
    public boolean hasStock(String wholesalerId, int quantity) {
        Map<String, Object> product = getWholesalerProduct(wholesalerId);
        
        if (product == null) {
            return false;
        }
        
        Integer inStock = (Integer) product.get("in_stock");
        return inStock != null && inStock >= quantity;
    }
    
    /**
     * Check if selling at retail price would be profitable.
     * Compares retail price against wholesale price.
     */
    public boolean isProfitable(String wholesalerId, double retailPrice) {
        Map<String, Object> product = getWholesalerProduct(wholesalerId);
        
        if (product == null) {
            return false;
        }
        
        Double wholesalePrice = (Double) product.get("price");
        return wholesalePrice != null && retailPrice > wholesalePrice;
    }
    
    //Get current wholesale price for a product.
    public Double getWholesalePrice(String wholesalerId) {
        Map<String, Object> product = getWholesalerProduct(wholesalerId);
        
        if (product == null) {
            return null;
        }
        
        return (Double) product.get("price");
    }
}