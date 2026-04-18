package com.joel.ordermanagement.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Public API shape for an Order — flattens the JPA entity graph into
 * a stable JSON contract with nested line items and a computed total.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String customerId;
    private List<OrderItemResponse> items;
    private BigDecimal total;
    private OrderStatus status;
    private LocalDateTime orderDate;

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                items,
                order.total(),
                order.getStatus(),
                order.getOrderDate()
        );
    }
}
