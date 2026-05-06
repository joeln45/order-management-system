package com.joel.ordermanagement.order;

import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.NotFoundException;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import com.joel.ordermanagement.wholesaler.WholesalerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService}. All collaborators are mocked: no Spring,
 * no database, no HTTP. Execution is sub-millisecond per test, so every branch
 * of the business logic gets its own case.
 *
 * <p>Naming convention: {@code methodUnderTest_condition_expectedOutcome}.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private WholesalerService wholesalerService;

    @InjectMocks private OrderService orderService;

    // -- fixtures -------------------------------------------------
    private Customer customer;
    private Product drill;
    private Product saw;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId("CUST001");
        customer.setName("Alice Example");
        customer.setEmail("alice@example.com");
        customer.setPostalAddress("10 Downing St");

        drill = new Product("Cordless Drill", new BigDecimal("129.99"), "wh-drill-1");
        drill.setId("prod-drill");

        saw  = new Product("Circular Saw", new BigDecimal("199.50"), "wh-saw-1");
        saw.setId("prod-saw");
    }

    // =============================================================
    // createOrder
    // =============================================================

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("saves a multi-item order with price snapshots and correct total")
        void createOrder_happyPath_savesWithSnapshots() {
            // -- arrange
            when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));
            when(productRepository.findById("prod-drill")).thenReturn(Optional.of(drill));
            when(productRepository.findById("prod-saw")).thenReturn(Optional.of(saw));
            when(wholesalerService.hasStock(anyString(), anyInt())).thenReturn(true);
            when(wholesalerService.isProfitable(anyString(), any(BigDecimal.class))).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateOrderRequest request = request("CUST001",
                    line("prod-drill", 2),
                    line("prod-saw", 1));

            // -- act
            Order result = orderService.createOrder(request);

            // -- assert: persisted order was the same object we built
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();

            assertThat(saved.getCustomer()).isEqualTo(customer);
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(saved.getItems()).hasSize(2);

            // price-at-purchase is snapshotted from the Product, not recomputed
            OrderItem drillLine = saved.getItems().get(0);
            assertThat(drillLine.getProduct()).isEqualTo(drill);
            assertThat(drillLine.getQuantity()).isEqualTo(2);
            assertThat(drillLine.getPriceAtPurchase()).isEqualByComparingTo("129.99");
            assertThat(drillLine.getOrder()).isSameAs(saved);  // bidirectional link wired

            // total = 129.99*2 + 199.50*1 = 459.48
            assertThat(result.total()).isEqualByComparingTo("459.48");
        }

        @Test
        @DisplayName("rejects an empty cart with BusinessRuleException")
        void createOrder_emptyItems_throwsBusinessRule() {
            CreateOrderRequest request = request("CUST001");  // no items

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("at least one item");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws NotFoundException when customer is unknown")
        void createOrder_unknownCustomer_throwsNotFound() {
            when(customerRepository.findById("ghost")).thenReturn(Optional.empty());

            CreateOrderRequest request = request("ghost", line("prod-drill", 1));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Customer")
                    .hasMessageContaining("ghost");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws NotFoundException when any product in the cart is unknown")
        void createOrder_unknownProduct_throwsNotFound() {
            when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));
            when(productRepository.findById("prod-ghost")).thenReturn(Optional.empty());

            CreateOrderRequest request = request("CUST001", line("prod-ghost", 1));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Product");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when wholesaler is out of stock")
        void createOrder_outOfStock_throwsBusinessRule() {
            when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));
            when(productRepository.findById("prod-drill")).thenReturn(Optional.of(drill));
            when(wholesalerService.hasStock("wh-drill-1", 5)).thenReturn(false);

            CreateOrderRequest request = request("CUST001", line("prod-drill", 5));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Insufficient stock");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when retail price is below wholesale")
        void createOrder_unprofitable_throwsBusinessRule() {
            when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));
            when(productRepository.findById("prod-drill")).thenReturn(Optional.of(drill));
            when(wholesalerService.hasStock("wh-drill-1", 1)).thenReturn(true);
            when(wholesalerService.isProfitable("wh-drill-1", drill.getRetailPrice())).thenReturn(false);

            CreateOrderRequest request = request("CUST001", line("prod-drill", 1));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not profitable");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("fails the entire order if any one line is invalid (transactional atomicity)")
        void createOrder_oneInvalidLine_rollsBackAll() {
            // drill is fine, saw is out of stock, so the whole order must fail.
            when(customerRepository.findById("CUST001")).thenReturn(Optional.of(customer));
            when(productRepository.findById("prod-drill")).thenReturn(Optional.of(drill));
            when(productRepository.findById("prod-saw")).thenReturn(Optional.of(saw));
            when(wholesalerService.hasStock("wh-drill-1", 1)).thenReturn(true);
            when(wholesalerService.isProfitable("wh-drill-1", drill.getRetailPrice())).thenReturn(true);
            when(wholesalerService.hasStock("wh-saw-1", 1)).thenReturn(false);

            CreateOrderRequest request = request("CUST001",
                    line("prod-drill", 1),
                    line("prod-saw", 1));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(BusinessRuleException.class);

            verify(orderRepository, never()).save(any());
        }
    }

    // =============================================================
    // cancelOrder
    // =============================================================

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("flips status PENDING → CANCELLED and saves")
        void cancelOrder_pending_flipsToCancelled() {
            Order order = new Order(customer);
            order.setId("ord-1");

            when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

            orderService.cancelOrder("ord-1");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("refuses to cancel an order that is already SHIPPED")
        void cancelOrder_nonPending_throws() {
            Order order = new Order(customer);
            order.setId("ord-1");
            order.setStatus(OrderStatus.SHIPPED);

            when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder("ord-1"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("SHIPPED");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws NotFoundException for unknown order id")
        void cancelOrder_unknown_throwsNotFound() {
            when(orderRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder("ghost"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // =============================================================
    // calculateCustomerRevenue
    // =============================================================

    @Nested
    @DisplayName("calculateCustomerRevenue")
    class CustomerRevenue {

        @Test
        @DisplayName("sums line totals and excludes CANCELLED orders")
        void revenue_sumsNonCancelledOnly() {
            Order pending = new Order(customer);
            pending.setStatus(OrderStatus.PENDING);
            pending.addItem(new OrderItem(drill, 2, new BigDecimal("100.00")));  // 200

            Order shipped = new Order(customer);
            shipped.setStatus(OrderStatus.SHIPPED);
            shipped.addItem(new OrderItem(saw, 1, new BigDecimal("50.00")));  // 50

            Order cancelled = new Order(customer);
            cancelled.setStatus(OrderStatus.CANCELLED);
            cancelled.addItem(new OrderItem(drill, 10, new BigDecimal("999.99")));  // excluded

            when(customerRepository.existsById("CUST001")).thenReturn(true);
            when(orderRepository.findByCustomer_Id("CUST001"))
                    .thenReturn(List.of(pending, shipped, cancelled));

            BigDecimal revenue = orderService.calculateCustomerRevenue("CUST001");

            assertThat(revenue).isEqualByComparingTo("250.00");  // 200 + 50, cancelled skipped
        }

        @Test
        @DisplayName("throws NotFoundException for unknown customer")
        void revenue_unknownCustomer_throwsNotFound() {
            when(customerRepository.existsById("ghost")).thenReturn(false);

            assertThatThrownBy(() -> orderService.calculateCustomerRevenue("ghost"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Customer");
        }

        @Test
        @DisplayName("returns ZERO when the customer has no orders")
        void revenue_noOrders_returnsZero() {
            when(customerRepository.existsById("CUST001")).thenReturn(true);
            when(orderRepository.findByCustomer_Id("CUST001")).thenReturn(List.of());

            assertThat(orderService.calculateCustomerRevenue("CUST001"))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =============================================================
    // Helpers
    // =============================================================

    private static CreateOrderRequest request(String customerId, CreateOrderRequest.LineItem... items) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        req.setItems(List.of(items));
        return req;
    }

    private static CreateOrderRequest.LineItem line(String productId, int quantity) {
        CreateOrderRequest.LineItem item = new CreateOrderRequest.LineItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
