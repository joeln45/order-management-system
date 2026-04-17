-- ==============================================================
-- V1: Initial schema for the Order Management System.
-- Creates the customers, products and orders tables with the
-- relationships and constraints needed for Phase 2.
-- ==============================================================

CREATE TABLE customers (
    id              VARCHAR(50)   PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    postal_address  VARCHAR(500)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id              VARCHAR(36)   PRIMARY KEY,           -- UUID stored as string
    description     VARCHAR(500)  NOT NULL,
    retail_price    DECIMAL(10,2) NOT NULL,
    wholesaler_id   VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id              VARCHAR(36)   PRIMARY KEY,
    customer_id     VARCHAR(50)   NOT NULL,
    product_id      VARCHAR(36)   NOT NULL,
    quantity        INTEGER       NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    order_date      TIMESTAMP     NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_orders_product  FOREIGN KEY (product_id)  REFERENCES products(id)
);

-- Indexes for common query patterns.
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);
