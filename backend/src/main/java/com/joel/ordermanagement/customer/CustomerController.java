package com.joel.ordermanagement.customer;

import com.joel.ordermanagement.order.Order;
import com.joel.ordermanagement.order.OrderService;
import com.joel.ordermanagement.order.OrderStatus;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Customer-facing REST API: browse products, place orders, view and cancel
 * own orders. All responses include HATEOAS links so clients can navigate
 * the API without hardcoding URLs.
 */
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class CustomerController {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;

    // ------------------------------------------------------------
    // Product endpoints
    // ------------------------------------------------------------

    /** GET /products — list all products with self-links. */
    @GetMapping("/products")
    public ResponseEntity<CollectionModel<EntityModel<Product>>> getAllProducts() {
        List<EntityModel<Product>> productModels = productRepository.findAll().stream()
                .map(product -> EntityModel.of(product,
                        linkTo(methodOn(CustomerController.class).getProduct(product.getId())).withSelfRel()))
                .collect(Collectors.toList());

        CollectionModel<EntityModel<Product>> collection = CollectionModel.of(productModels);
        collection.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withSelfRel());

        return ResponseEntity.ok(collection);
    }

    /** GET /products/{id} — single product with links to all-products and create-order. */
    @GetMapping("/products/{id}")
    public ResponseEntity<EntityModel<Product>> getProduct(@PathVariable String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        EntityModel<Product> model = EntityModel.of(product);
        model.add(linkTo(methodOn(CustomerController.class).getProduct(id)).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withRel("all-products"));
        model.add(linkTo(methodOn(CustomerController.class).createOrder(null)).withRel("create-order"));

        return ResponseEntity.ok(model);
    }

    // ------------------------------------------------------------
    // Order endpoints
    // ------------------------------------------------------------

    /** POST /orders — place a new order; validates customer, product, stock, profitability. */
    @PostMapping("/orders")
    public ResponseEntity<EntityModel<Order>> createOrder(@RequestBody OrderRequest request) {
        if (request == null || request.getCustomerId() == null
                || request.getProductId() == null || request.getQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required fields: customerId, productId, quantity");
        }

        Order order = orderService.createOrder(
                request.getCustomerId(), request.getProductId(), request.getQuantity());

        EntityModel<Order> model = EntityModel.of(order);
        model.add(linkTo(methodOn(CustomerController.class).getOrder(order.getId())).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(order.getCustomerId()))
                .withRel("customer-orders"));
        model.add(linkTo(methodOn(CustomerController.class).getProduct(order.getProductId()))
                .withRel("product"));
        model.add(linkTo(methodOn(CustomerController.class).cancelOrder(order.getId()))
                .withRel("cancel"));

        return ResponseEntity.status(HttpStatus.CREATED).body(model);
    }

    /** GET /orders/{id} — single order; cancel link only present when status is PENDING. */
    @GetMapping("/orders/{id}")
    public ResponseEntity<EntityModel<Order>> getOrder(@PathVariable String id) {
        Order order = orderService.getOrder(id);

        EntityModel<Order> model = EntityModel.of(order);
        model.add(linkTo(methodOn(CustomerController.class).getOrder(id)).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getProduct(order.getProductId()))
                .withRel("product"));
        model.add(linkTo(methodOn(CustomerController.class).getCustomer(order.getCustomerId()))
                .withRel("customer"));
        model.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(order.getCustomerId()))
                .withRel("customer-orders"));

        if (order.getStatus() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(CustomerController.class).cancelOrder(id)).withRel("cancel"));
        }
        return ResponseEntity.ok(model);
    }

    /** GET /customers/{customerId}/orders — list a customer's orders. */
    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<CollectionModel<EntityModel<Order>>> getCustomerOrders(
            @PathVariable String customerId) {

        List<EntityModel<Order>> orderModels = orderService.getOrdersByCustomer(customerId).stream()
                .map(order -> {
                    EntityModel<Order> model = EntityModel.of(order);
                    model.add(linkTo(methodOn(CustomerController.class).getOrder(order.getId())).withSelfRel());
                    model.add(linkTo(methodOn(CustomerController.class).getProduct(order.getProductId()))
                            .withRel("product"));
                    if (order.getStatus() == OrderStatus.PENDING) {
                        model.add(linkTo(methodOn(CustomerController.class).cancelOrder(order.getId()))
                                .withRel("cancel"));
                    }
                    return model;
                })
                .collect(Collectors.toList());

        CollectionModel<EntityModel<Order>> collection = CollectionModel.of(orderModels);
        collection.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(customerId)).withSelfRel());
        collection.add(linkTo(methodOn(CustomerController.class).getCustomer(customerId)).withRel("customer"));

        return ResponseEntity.ok(collection);
    }

    /** DELETE /orders/{id} — cancel order; only allowed when status is PENDING. */
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // Customer profile endpoint
    // ------------------------------------------------------------

    /** GET /customers/{id} — customer profile with links to orders and product catalogue. */
    @GetMapping("/customers/{id}")
    public ResponseEntity<EntityModel<Customer>> getCustomer(@PathVariable String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        EntityModel<Customer> model = EntityModel.of(customer);
        model.add(linkTo(methodOn(CustomerController.class).getCustomer(id)).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(id)).withRel("orders"));
        model.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withRel("products"));

        return ResponseEntity.ok(model);
    }

    // ------------------------------------------------------------
    // Request DTOs
    // ------------------------------------------------------------

    /** Body for POST /orders. */
    @Data
    public static class OrderRequest {
        private String customerId;
        private String productId;
        private Integer quantity;
    }
}
