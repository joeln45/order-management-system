-- ==============================================================
-- V2: Seed the three baseline customers required by the assignment.
-- Flyway records this migration's hash in flyway_schema_history once it
-- runs successfully — it will never be re-applied to the same database,
-- so we don't need an idempotent insert pattern here.
-- ==============================================================

INSERT INTO customers (id, name, email, postal_address) VALUES
    ('CUST001', 'Michelle James', 'michelle.james@example.com', '12 Bridge Street, Stirling FK8 1AA'),
    ('CUST002', 'Katelyn James',  'katelyn.james@example.com',  '45 King Road, Glasgow G1 2BB'),
    ('CUST003', 'Steve Brown',    'steve.brown@example.com',    '7 Castle Avenue, Edinburgh EH1 3CC');
