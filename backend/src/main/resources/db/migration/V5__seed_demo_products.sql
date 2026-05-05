-- ==============================================================
-- V5: Seed demo products so the catalogue is populated even when
-- the external wholesaler API is unavailable.
--
-- These use fixed UUIDs so they're stable across restarts.
-- The wholesaler_id values match demo-* placeholders; the
-- WholesalerService falls back to local data for these ids
-- when the external API returns null (see WholesalerService).
-- ==============================================================

INSERT INTO products (id, description, retail_price, wholesaler_id) VALUES
    ('a1b2c3d4-0001-0000-0000-000000000001', 'Cordless Drill 18V 2-Speed',             89.99,  'demo-drill-001'),
    ('a1b2c3d4-0001-0000-0000-000000000002', 'Hammer Drill 850W SDS-Plus',              129.99, 'demo-drill-002'),
    ('a1b2c3d4-0001-0000-0000-000000000003', 'Brushless Combi Drill 20V',               149.99, 'demo-drill-003'),
    ('a1b2c3d4-0001-0000-0000-000000000004', 'Right-Angle Drill Attachment Kit',         39.99, 'demo-drill-004'),
    ('a1b2c3d4-0001-0000-0000-000000000005', 'Drill Bit Set 170-Piece (HSS + Masonry)',  24.99, 'demo-drill-005');
