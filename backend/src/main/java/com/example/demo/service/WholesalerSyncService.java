package com.example.demo.service;

import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * Service that syncs with the wholesaler's catalog using all 3 API endpoints.
 * 1. Root endpoint discovers categories
 * 2. Category endpoints discovers products in each category
 * 3. Product endpoints gets details for each product
 */
@Service
public class WholesalerSyncService {
    
    private static final String WHOLESALER_BASE_URL = "https://pmaier.eu.pythonanywhere.com/wss";
    
    private final RestTemplate restTemplate;
    private final ProductRepository productRepository;
    
    public WholesalerSyncService(ProductRepository productRepository) {
        this.restTemplate = new RestTemplate();
        this.productRepository = productRepository;
    }
    
    // I have use AI for helping me to integrate wholesellers api
    //Sync all products from wholesaler (all categories).
    @SuppressWarnings("unchecked")
    public void syncFullCatalog() {
    	// Sync products from wholesaler
    	System.out.println("Starting sync...");
    	List<String> categories = getCategories();
        
        int totalProductsAdded = 0;
        
        // Get products for each category
        for (String category : categories) {
            System.out.println("Processing category: " + category);
            List<Map<String, Object>> products = getProductsInCategory(category);
            System.out.println("  Found " + products.size() + " products in " + category);
            
            // Get full details for each product
            for (Map<String, Object> productSummary : products) {
                // Getd product ID
                String wholesalerId = null;
                
                // Check if 'id' field exists directly
                if (productSummary.containsKey("id")) {
                    wholesalerId = (String) productSummary.get("id");
                }
                
                // If not, try to extract from _links
                if (wholesalerId == null && productSummary.containsKey("_links")) {
                    Map<String, Object> links = (Map<String, Object>) productSummary.get("_links");
                    if (links.containsKey("self")) {
                        Map<String, Object> self = (Map<String, Object>) links.get("self");
                        String href = (String) self.get("href");
                        // Extract ID from URLs such as -> /product/QR4P6EEX
                        if (href != null && href.contains("/product/")) {
                            wholesalerId = href.substring(href.lastIndexOf("/") + 1);
                        }
                    }
                }
                
                if (wholesalerId != null && !wholesalerId.isEmpty()) {
                    // Get full product details
                    Map<String, Object> productDetails = getProductDetails(wholesalerId);
                    
                    if (productDetails != null) {
                        // Add to our database if profitable
                        boolean added = addProductToDatabase(productDetails);
                        if (added) {
                            totalProductsAdded++;
                        }
                    }
                } else {
                    System.out.println("    ⊘ Could not extract product ID");
                }
            }
            System.out.println();
        }
        
        System.out.println("\n");
        System.out.println("SYNC COMPLETE!");
        System.out.println("Total products: " + productRepository.count());
        System.out.println("New products added: " + totalProductsAdded);
        System.out.println("\n");
    }
    
    //Call wholesaler root endpoint to get categories.
    @SuppressWarnings("unchecked")
    private List<String> getCategories() {
        System.out.println("STEP 1: Calling root endpoint (API #1)");
        System.out.println("URL: " + WHOLESALER_BASE_URL);
        
        try {
            Map<String, Object> response = restTemplate.getForObject(WHOLESALER_BASE_URL, Map.class);
            
            List<String> categories = new ArrayList<>();
            
            if (response != null && response.containsKey("_embedded")) {
                Map<String, Object> embedded = (Map<String, Object>) response.get("_embedded");
                List<Map<String, Object>> categoriesList = (List<Map<String, Object>>) embedded.get("categories");
                
                for (Map<String, Object> cat : categoriesList) {
                    String categoryName = (String) cat.get("category");
                    categories.add(categoryName);
                    System.out.println("  - " + categoryName);
                }
            }
            
            return categories;
            
        } catch (Exception e) {
            System.out.println("ERROR fetching categories: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    //Get products in a category from wholesaler.
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getProductsInCategory(String category) {
        String url = WHOLESALER_BASE_URL + "/category/" + category;
        System.out.println("  STEP 2: Calling category endpoint (API #2)");
        System.out.println("  URL: " + url);
        
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            List<Map<String, Object>> products = new ArrayList<>();
            
            if (response != null && response.containsKey("_embedded")) {
                Map<String, Object> embedded = (Map<String, Object>) response.get("_embedded");
                Object productsObj = embedded.get("products");
                
                if (productsObj instanceof List) {
                    products = (List<Map<String, Object>>) productsObj;
                    System.out.println("  Found " + products.size() + " products");
                }
            }
            
            return products;
            
        } catch (Exception e) {
            System.out.println("  ERROR fetching products in category: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    //Get full product details by ID from wholesaler.
    @SuppressWarnings("unchecked")
    private Map<String, Object> getProductDetails(String wholesalerId) {
        String url = WHOLESALER_BASE_URL + "/product/" + wholesalerId;
        
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response;
            
        } catch (Exception e) {
            System.out.println("    ERROR fetching product " + wholesalerId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Add product to our database if it's profitable.
     * We mark up wholesale price by 30% to set our retail price.
     */
    private boolean addProductToDatabase(Map<String, Object> productDetails) {
        String wholesalerId = (String) productDetails.get("id");
        String description = (String) productDetails.get("description");
        Object priceObj = productDetails.get("price");
        
        // Check if product already exists
        if (productRepository.findAll().stream()
                .anyMatch(p -> p.getWholesalerId().equals(wholesalerId))) {
            System.out.println("    ⊘ Product " + wholesalerId + " already exists, skipping");
            return false;
        }
        
        if (priceObj != null) {
            double wholesalePrice = ((Number) priceObj).doubleValue();
            
            // Set our retail price with 30% markup
            double retailPrice = wholesalePrice * 1.30;
            
            // Round to 2 decimal places
            retailPrice = Math.round(retailPrice * 100.0) / 100.0;
            
            // Create and save product
            Product product = new Product(description, retailPrice, wholesalerId);
            productRepository.save(product);
            
            System.out.println("    ✓ Added: " + description);
            System.out.println("      Wholesale: £" + wholesalePrice + " → Retail: £" + retailPrice);
            
            return true;
        }
        
        return false;
    }
    
    //Syncs only drills category 
    @SuppressWarnings("unchecked")
    public void syncDrillsOnly() {
        System.out.println("\n");
        System.out.println("QUICK SYNC - DRILLS ONLY");
        System.out.println("\n");
        
        List<Map<String, Object>> products = getProductsInCategory("drills");
        int added = 0;
        
        for (Map<String, Object> productSummary : products) {
            // Try to get ID from different possible locations
            String wholesalerId = null;
            
            // Check if 'id' field exists 
            if (productSummary.containsKey("id")) {
                wholesalerId = (String) productSummary.get("id");
            }
            
            // If not, try to extract from _links
            if (wholesalerId == null && productSummary.containsKey("_links")) {
                Map<String, Object> links = (Map<String, Object>) productSummary.get("_links");
                if (links.containsKey("self")) {
                    Map<String, Object> self = (Map<String, Object>) links.get("self");
                    String href = (String) self.get("href");
                    // Extract ID from url such as /product/QR4P6EEX
                    if (href != null && href.contains("/product/")) {
                        wholesalerId = href.substring(href.lastIndexOf("/") + 1);
                    }
                }
            }
            
            if (wholesalerId != null && !wholesalerId.isEmpty()) {
                System.out.println("  Processing product: " + wholesalerId);
                Map<String, Object> productDetails = getProductDetails(wholesalerId);
                
                if (productDetails != null) {
                    if (addProductToDatabase(productDetails)) {
                        added++;
                    }
                }
            } else {
                System.out.println("  ⊘ Could not extract product ID from: " + productSummary);
            }
        }
        
        System.out.println("\nDrills sync complete! Added " + added + " products.\n");
    }
}