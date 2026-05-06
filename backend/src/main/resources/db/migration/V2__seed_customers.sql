-- V2: Seed the three baseline customers required by the assignment.
-- Once Flyway records this migration's hash in flyway_schema_history
-- it won't be re-run on the same database, so a plain INSERT is fine.

INSERT INTO customers (id, name, email, postal_address) VALUES
    ('CUST001', 'Michelle James', 'michelle.james@example.com', '12 Bridge Street, Stirling FK8 1AA'),
    ('CUST002', 'Katelyn James',  'katelyn.james@example.com',  '45 King Road, Glasgow G1 2BB'),
    ('CUST003', 'Steve Brown',    'steve.brown@example.com',    '7 Castle Avenue, Edinburgh EH1 3CC');
