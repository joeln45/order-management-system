package com.joel.ordermanagement.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.NotFoundException;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OperatorController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OperatorControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @MockBean private OrderService orderService;
    @MockBean private ProductRepository productRepository;
    @MockBean private CustomerRepository customerRepository;
    @MockBean private com.joel.ordermanagement.auth.JwtService jwtService;  // satisfies JwtAuthFilter dep in the slice

    private Customer customer;
    private Product drill;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId("CUST001");
        customer.setName("Alice Example");
        customer.setEmail("alice@example.com");
        customer.setPostalAddress("10 Downing St");

        drill = new Product("Cordless Drill", new BigDecimal("129.99"), "wh-drill-1");
        drill.setId("prod-drill");
    }

    // =============================================================
    // GET /operator/orders
    // =============================================================

    @Test
    void getAllOrders_returns200HateoasCollection() throws Exception {
        Order order = new Order(customer);
        order.setId("ord-1");
        order.addItem(new OrderItem(drill, 1, new BigDecimal("129.99")));

        when(orderService.getAllOrders()).thenReturn(List.of(order));

        mvc.perform(get("/operator/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.orderResponseList[0].id").value("ord-1"))
                .andExpect(jsonPath("$._embedded.orderResponseList[0]._links['update-status'].href").exists())
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    // =============================================================
    // PUT /operator/orders/{id}/status
    // =============================================================

    @Test
    void updateOrderStatus_validBody_returns204AndDelegates() throws Exception {
        OperatorController.StatusUpdate body = new OperatorController.StatusUpdate();
        body.setStatus(OrderStatus.SHIPPED);

        mvc.perform(put("/operator/orders/ord-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        verify(orderService).updateOrderStatus(eq("ord-1"), eq(OrderStatus.SHIPPED));
    }

    @Test
    void updateOrderStatus_missingStatus_returns409ProblemDetail() throws Exception {
        mvc.perform(put("/operator/orders/ord-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))   // body present but status is null
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violated"))
                .andExpect(jsonPath("$.detail").value("Missing status in request body"));
    }

    @Test
    void updateOrderStatus_unknownOrder_surfacesAs404() throws Exception {
        OperatorController.StatusUpdate body = new OperatorController.StatusUpdate();
        body.setStatus(OrderStatus.SHIPPED);

        doThrow(NotFoundException.of("Order", "ghost"))
                .when(orderService).updateOrderStatus(anyString(), eq(OrderStatus.SHIPPED));

        mvc.perform(put("/operator/orders/ghost/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // =============================================================
    // PUT /operator/products/{id}/price
    // =============================================================

    @Test
    void updateProductPrice_validBody_returns204() throws Exception {
        OperatorController.PriceUpdate body = new OperatorController.PriceUpdate();
        body.setRetailPrice(new BigDecimal("149.99"));

        when(productRepository.findById("prod-drill")).thenReturn(Optional.of(drill));
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/operator/products/prod-drill/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateProductPrice_missingPrice_returns409ProblemDetail() throws Exception {
        mvc.perform(put("/operator/products/prod-drill/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Missing retailPrice in request body"));
    }

    @Test
    void updateProductPrice_unknownProduct_returns404() throws Exception {
        OperatorController.PriceUpdate body = new OperatorController.PriceUpdate();
        body.setRetailPrice(new BigDecimal("149.99"));

        when(productRepository.findById("ghost")).thenReturn(Optional.empty());

        mvc.perform(put("/operator/products/ghost/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // =============================================================
    // GET /operator/customers/{id}/revenue
    // =============================================================

    @Test
    void getCustomerRevenue_returns200WithTotal() throws Exception {
        when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));
        when(orderService.calculateCustomerRevenue("CUST001")).thenReturn(new BigDecimal("1284.50"));

        mvc.perform(get("/operator/customers/CUST001/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST001"))
                .andExpect(jsonPath("$.customerName").value("Alice Example"))
                .andExpect(jsonPath("$.totalRevenue").value(1284.50));
    }

    @Test
    void getCustomerRevenue_unknownCustomer_returns404() throws Exception {
        when(customerRepository.findById("ghost")).thenReturn(Optional.empty());

        mvc.perform(get("/operator/customers/ghost/revenue"))
                .andExpect(status().isNotFound());
    }
}
