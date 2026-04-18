-- ==============================================================
-- V3: Split one-product orders into a header (orders) + line items
-- (order_items) model so a single order can contain multiple products.
--
-- price_at_purchase snapshots the retail price at order time — later
-- product price changes must never rewrite historical order totals.
-- ==============================================================

CREATE TABLE order_items (
    id                 VARCHAR(36)    PRIMARY KEY,
    order_id           VARCHAR(36)    NOT NULL,
    product_id         VARCHAR(36)    NOT NULL,
    quantity           INTEGER        NOT NULL,
    price_at_purchase  DECIMAL(10,2)  NOT NULL,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders(id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_order_items_order_id   ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- Backfill any existing single-product orders into the new line-item table.
-- Uses the product's current retail price as the historical snapshot, which
-- is the best we can do — anything booked after V3 gets the real snapshot.
-- (No-op when orders is empty, which is the typical dev/test case.)
INSERT INTO order_items (id, order_id, product_id, quantity, price_at_purchase, created_at, updated_at)
SELECT GEN_RANDOM_UUID(), o.id, o.product_id, o.quantity, p.retail_price, o.created_at, o.updated_at
FROM orders o
JOIN products p ON p.id = o.product_id;

-- Drop the legacy single-product columns from the orders header.
ALTER TABLE orders DROP CONSTRAINT fk_orders_product;
ALTER TABLE orders DROP COLUMN product_id;
ALTER TABLE orders DROP COLUMN quantity;
