-- ==============================================================
-- DEV SEED: product_db
-- Cập nhật @seller1_id trước khi chạy:
--   http://localhost:8180 → realm agricontract → Users → seller account → UUID
-- ==============================================================

SET @seller1_id = '1a5a2ddd-56b9-492a-b966-99f436e0fc85';

-- Products
INSERT IGNORE INTO products (product_id, name, unit, category, created_at, updated_at) VALUES
  ('prod-seed-rice',   'Gạo tẻ ST25',           'kg', 'GRAIN',  NOW(), NOW()),
  ('prod-seed-coffee', 'Cà phê Robusta',         'kg', 'COFFEE', NOW(), NOW()),
  ('prod-seed-pepper', 'Hồ tiêu đen',            'kg', 'SPICE',  NOW(), NOW()),
  ('prod-seed-durian', 'Sầu riêng Musang King',  'kg', 'FRUIT',  NOW(), NOW());

-- Listings — ACTIVE (để browse + dùng cho OFFERED/NEGOTIATING contract)
INSERT IGNORE INTO listings (listing_id, seller_id, product_id, product_name, quantity, quantity_unit, price_floor, currency, delivery_deadline, status, created_at, updated_at)
VALUES
  ('lst-seed-browse-01', @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',          1000.000, 'kg',  8000.00, 'VND', '2026-10-15', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-browse-02', @seller1_id, 'prod-seed-coffee', 'Cà phê Robusta',         500.000, 'kg', 55000.00, 'VND', '2026-11-30', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-browse-03', @seller1_id, 'prod-seed-pepper', 'Hồ tiêu đen',            100.000, 'kg',120000.00, 'VND', '2026-09-30', 'ACTIVE', NOW(), NOW()),
  -- Dùng bởi OFFERED contract
  ('lst-seed-offered',   @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',            800.000, 'kg',  8500.00, 'VND', '2026-10-01', 'ACTIVE', NOW(), NOW()),
  -- Dùng bởi NEGOTIATING contract
  ('lst-seed-neg',       @seller1_id, 'prod-seed-coffee', 'Cà phê Robusta',          300.000, 'kg', 60000.00, 'VND', '2026-11-01', 'ACTIVE', NOW(), NOW()),
  -- Dùng bởi SIGNED → DISPUTED contracts (đã CLOSED khi ký hợp đồng)
  ('lst-seed-signed',    @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',            500.000, 'kg',  8000.00, 'VND', '2026-10-01', 'CLOSED', NOW(), NOW()),
  ('lst-seed-active',    @seller1_id, 'prod-seed-pepper', 'Hồ tiêu đen',            200.000, 'kg',110000.00, 'VND', '2026-09-15', 'CLOSED', NOW(), NOW()),
  ('lst-seed-delivered', @seller1_id, 'prod-seed-coffee', 'Cà phê Robusta',          300.000, 'kg', 58000.00, 'VND', '2026-10-15', 'CLOSED', NOW(), NOW()),
  ('lst-seed-settled',   @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',           1000.000, 'kg',  7800.00, 'VND', '2026-08-31', 'CLOSED', NOW(), NOW()),
  ('lst-seed-cancelled', @seller1_id, 'prod-seed-durian', 'Sầu riêng Musang King',  400.000, 'kg', 85000.00, 'VND', '2026-09-01', 'CLOSED', NOW(), NOW()),
  ('lst-seed-disputed',  @seller1_id, 'prod-seed-pepper', 'Hồ tiêu đen',            150.000, 'kg',125000.00, 'VND', '2026-09-30', 'CLOSED', NOW(), NOW());
