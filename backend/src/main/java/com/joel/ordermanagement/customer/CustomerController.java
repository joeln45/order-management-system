package com.joel.ordermanagement.customer;

import com.joel.ordermanagement.order.CreateOrderRequest;
import com.joel.ordermanagement.order.Order;
import com.joel.ordermanagement.order.OrderResponse;
import com.joel.ordermanagement.order.OrderService;
import com.joel.ordermanagement.order.OrderStatus;
import com.joel.ordermanagement.product.Product;
import com.joel.ordermanagement.product.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import com.joel.ordermanagement.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
@Tags({
        @Tag(name = "Products", description = "Browse the product catalogue (public)"),
        @Tag(name = "Orders", description = "Place, view and cancel customer orders"),
        @Tag(name = "Customers", description = "Customer profile endpoints")
})
public class CustomerController {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;

    // ------------------------------------------------------------
    // Product endpoints (public)
    // ------------------------------------------------------------

    @GetMapping("/products")
    @Tag(name = "Products")
    @SecurityRequirements({})  // public endpoint, no bearer token required
    @Operation(summary = "List all products", description = "Returns the full catalogue with HATEOAS self-links.")
    @ApiResponse(responseCode = "200", description = "Product list")
    public ResponseEntity<CollectionModel<EntityModel<Product>>> getAllProducts() {
        List<EntityModel<Product>> productModels = productRepository.findAll().stream()
                .map(product -> EntityModel.of(product,
                        linkTo(methodOn(CustomerController.class).getProduct(product.getId())).withSelfRel()))
                .collect(Collectors.toList());

        CollectionModel<EntityModel<Product>> collection = CollectionModel.of(productModels);
        collection.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withSelfRel());

        return ResponseEntity.ok(collection);
    }

    @GetMapping("/products/{id}")
    @Tag(name = "Products")
    @SecurityRequirements({})
    @Operation(summary = "Get a single product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<EntityModel<Product>> getProduct(
            @Parameter(description = "Product id", example = "prod-123") @PathVariable String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));

        EntityModel<Product> model = EntityModel.of(product);
        model.add(linkTo(methodOn(CustomerController.class).getProduct(id)).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withRel("all-products"));
        model.add(linkTo(methodOn(CustomerController.class).createOrder(null)).withRel("create-order"));

        return ResponseEntity.ok(model);
    }

    // ------------------------------------------------------------
    // Order endpoints (require ROLE_CUSTOMER)
    // ------------------------------------------------------------

    @PostMapping("/orders")
    @Tag(name = "Orders")
    @Operation(
            summary = "Place a new multi-item order",
            description = """
                    Validates customer existence, per-line product existence, stock availability
                    at the wholesaler, and profitability against the current wholesale price.
                    Whole request is one DB transaction; partial orders can't land.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a customer"),
            @ApiResponse(responseCode = "404", description = "Customer or product not found"),
            @ApiResponse(responseCode = "409", description = "Business rule violation (out of stock, unprofitable, ...)")
    })
    public ResponseEntity<EntityModel<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toModel(order));
    }

    @GetMapping("/orders/{id}")
    @Tag(name = "Orders")
    @Operation(summary = "Get a single order", description = "Cancel link appears only when status is PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<EntityModel<OrderResponse>> getOrder(
            @Parameter(description = "Order id", example = "ord-42") @PathVariable String id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(toModel(order));
    }

    @GetMapping("/customers/{customerId}/orders")
    @Tag(name = "Orders")
    @Operation(summary = "List a customer's orders")
    public ResponseEntity<CollectionModel<EntityModel<OrderResponse>>> getCustomerOrders(
            @Parameter(description = "Customer id", example = "CUST001") @PathVariable String customerId) {

        List<EntityModel<OrderResponse>> orderModels = orderService.getOrdersByCustomer(customerId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());

        CollectionModel<EntityModel<OrderResponse>> collection = CollectionModel.of(orderModels);
        collection.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(customerId)).withSelfRel());
        collection.add(linkTo(methodOn(CustomerController.class).getCustomer(customerId)).withRel("customer"));

        return ResponseEntity.ok(collection);
    }

    @DeleteMapping("/orders/{id}")
    @Tag(name = "Orders")
    @Operation(summary = "Cancel a PENDING order")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Order cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order is not in PENDING status")
    })
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // Customer profile endpoint
    // ------------------------------------------------------------

    @GetMapping("/customers/{id}")
    @Tag(name = "Customers")
    @Operation(summary = "Get a customer profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    public ResponseEntity<EntityModel<Customer>> getCustomer(
            @Parameter(description = "Customer id", example = "CUST001") @PathVariable String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Customer", id));

        EntityModel<Customer> model = EntityModel.of(customer);
        model.add(linkTo(methodOn(CustomerController.class).getCustomer(id)).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(id)).withRel("orders"));
        model.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withRel("products"));

        return ResponseEntity.ok(model);
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    /** Wrap an {@link Order} in an {@link OrderResponse} EntityModel with HATEOAS links. */
    private EntityModel<OrderResponse> toModel(Order order) {
        OrderResponse body = OrderResponse.from(order);
        EntityModel<OrderResponse> model = EntityModel.of(body);
        model.add(linkTo(methodOn(CustomerController.class).getOrder(body.getId())).withSelfRel());
        model.add(linkTo(methodOn(CustomerController.class).getCustomer(body.getCustomerId())).withRel("customer"));
        model.add(linkTo(methodOn(CustomerController.class).getCustomerOrders(body.getCustomerId()))
                .withRel("customer-orders"));
        model.add(linkTo(methodOn(CustomerController.class).getAllProducts()).withRel("products"));
        if (body.getStatus() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(CustomerController.class).cancelOrder(body.getId())).withRel("cancel"));
        }
        return model;
    }
}
