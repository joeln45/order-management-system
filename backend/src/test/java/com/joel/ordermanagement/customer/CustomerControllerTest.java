package com.joel.ordermanagement.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.NotFoundException;
import com.joel.ordermanagement.order.CreateOrderRequest;
import com.joel.ordermanagement.order.Order;
import com.joel.ordermanagement.order.OrderItem;
import com.joel.ordermanagement.order.OrderService;
import com.joel.ordermanagement.order.OrderStatus;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link CustomerController}. Security filters disabled — we're
 * testing request mapping, validation, HATEOAS envelope shape, and error
 * translation, not authorisation.
 */
@WebMvcTest(controllers = CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class CustomerControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @MockBean private ProductRepository productRepository;
    @MockBean private CustomerRepository customerRepository;
    @MockBean private OrderService orderService;
    @MockBean private com.joel.ordermanagement.auth.JwtService jwtService;  // satisfies JwtAuthFilter dep in the slice

    private Product drill;
    private Customer customer;

    @BeforeEach
    void setUp() {
        drill = new Product("Cordless Drill", new BigDecimal("129.99"), "wh-drill-1");
        drill.setId("prod-drill");

        customer = new Customer();
        customer.setId("CUST001");
        customer.setName("Alice Example");
        customer.setEmail("alice@example.com");
        customer.setPostalAddress("10 Downing St");
    }

    // =============================================================
    // GET /products
    // =============================================================

    @Test
    void getAllProducts_returns200WithHateoasCollection() throws Exception {
        when(productRepository.findAll()).thenReturn(List.of(drill));

        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.productList[0].description").value("Cordless Drill"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    void getProduct_existing_returns200WithLinks() throws Exception {
        when(productRepository.findById("prod-drill")).thenReturn(Optional.of(drill));

        mvc.perform(get("/products/prod-drill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Cordless Drill"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links['all-products'].href").exists());
    }

    @Test
    void getProduct_unknown_returns404ProblemDetail() throws Exception {
        when(productRepository.findById("ghost")).thenReturn(Optional.empty());

        mvc.perform(get("/products/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Product")));
    }

    // =============================================================
    // POST /orders — validation
    // =============================================================

    @Test
    void createOrder_emptyItems_returns400() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId("CUST001");
        req.setItems(List.of());   // @NotEmpty violation

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void createOrder_negativeQuantity_returns400() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId("CUST001");
        CreateOrderRequest.LineItem line = new CreateOrderRequest.LineItem();
        line.setProductId("prod-drill");
        line.setQuantity(-1);  // @Positive violation
        req.setItems(List.of(line));

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['items[0].quantity']").exists());
    }

    @Test
    void createOrder_happyPath_returns201() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId("CUST001");
        CreateOrderRequest.LineItem line = new CreateOrderRequest.LineItem();
        line.setProductId("prod-drill");
        line.setQuantity(2);
        req.setItems(List.of(line));

        Order saved = new Order(customer);
        saved.setId("ord-42");
        saved.setStatus(OrderStatus.PENDING);
        saved.addItem(new OrderItem(drill, 2, new BigDecimal("129.99")));

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(saved);

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ord-42"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(259.98))
                .andExpect(jsonPath("$._links.cancel.href").exists()); // PENDING → cancel link present
    }

    @Test
    void createOrder_outOfStock_surfacesAs409() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId("CUST001");
        CreateOrderRequest.LineItem line = new CreateOrderRequest.LineItem();
        line.setProductId("prod-drill");
        line.setQuantity(999);
        req.setItems(List.of(line));

        when(orderService.createOrder(any(CreateOrderRequest.class)))
                .thenThrow(new BusinessRuleException("Insufficient stock for product: Cordless Drill"));

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violated"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));
    }

    // =============================================================
    // DELETE /orders/{id}
    // =============================================================

    @Test
    void cancelOrder_returns204() throws Exception {
        mvc.perform(delete("/orders/ord-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelOrder_nonPending_surfacesAs409() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessRuleException("Cannot cancel order — status is SHIPPED"))
                .when(orderService).cancelOrder(anyString());

        mvc.perform(delete("/orders/ord-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("SHIPPED")));
    }

    // =============================================================
    // GET /customers/{id}
    // =============================================================

    @Test
    void getCustomer_existing_returns200WithLinks() throws Exception {
        when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));

        mvc.perform(get("/customers/CUST001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Example"))
                .andExpect(jsonPath("$._links.orders.href").exists());
    }

    @Test
    void getCustomer_unknown_returns404() throws Exception {
        when(customerRepository.findById("ghost")).thenReturn(Optional.empty());

        mvc.perform(get("/customers/ghost"))
                .andExpect(status().isNotFound());
    }
}
