SET NAMES utf8mb4;
-- ==============================================================
-- DEV SEED: product_db
-- Cập nhật @seller1_id trước khi chạy:
--   http://localhost:8180 → realm agricontract → Users → seller account → UUID
-- ==============================================================

SET @seller1_id = '1a5a2ddd-56b9-492a-b966-99f436e0fc85';

-- Categories (APPROVED — products cần category_id trỏ tới category đã approved)
-- Fixed UUIDs: the frozen contract types categoryId/productId as uuid (a non-UUID id broke the contract snapshot, #428).
-- Only coffee is mapped (Phase 2 scope is coffee only; other categories stay commodity NULL).
INSERT IGNORE INTO categories (category_id, name, normalized_name, status, commodity, proposed_by, proposed_by_email, created_at, updated_at) VALUES
  ('5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a01',  'Ngũ cốc',  'ngu coc',  'APPROVED', NULL, @seller1_id, 'seller1@agricontract.dev', NOW(), NOW()),
  ('5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a02', 'Cà phê',   'ca phe',   'APPROVED', 'COFFEE', @seller1_id, 'seller1@agricontract.dev', NOW(), NOW()),
  ('5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a03',  'Gia vị',   'gia vi',   'APPROVED', NULL, @seller1_id, 'seller1@agricontract.dev', NOW(), NOW()),
  ('5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a04',  'Trái cây', 'trai cay', 'APPROVED', NULL, @seller1_id, 'seller1@agricontract.dev', NOW(), NOW());

-- Products
INSERT IGNORE INTO products (product_id, name, unit, category_id, images, created_at, updated_at) VALUES
  ('7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b01',   'Gạo tẻ ST25',           'kg', '5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a01',  JSON_ARRAY('https://picsum.photos/seed/prod-seed-rice/800'),   NOW(), NOW()),
  ('7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b02', 'Cà phê Robusta',         'kg', '5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a02', JSON_ARRAY('https://picsum.photos/seed/prod-seed-coffee/800'), NOW(), NOW()),
  ('7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b03', 'Hồ tiêu đen',            'kg', '5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a03',  JSON_ARRAY('https://picsum.photos/seed/prod-seed-pepper/800'), NOW(), NOW()),
  ('7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b04', 'Sầu riêng Musang King',  'kg', '5b0c1a4e-7d2f-4c61-9a3b-0e8f2d6c1a04',  JSON_ARRAY('https://picsum.photos/seed/prod-seed-durian/800'), NOW(), NOW());

-- Listings — ACTIVE (để browse + dùng cho OFFERED/NEGOTIATING contract)
INSERT IGNORE INTO listings (listing_id, seller_id, product_id, product_name, quantity, quantity_unit, price_floor, currency, delivery_deadline, status, created_at, updated_at)
VALUES
  ('lst-seed-browse-01', @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b01',   'Gạo tẻ ST25',          1000.000, 'kg',  8000.00, 'VND', '2026-10-15', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-browse-02', @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b02', 'Cà phê Robusta',         500.000, 'kg', 55000.00, 'VND', '2026-11-30', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-browse-03', @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b03', 'Hồ tiêu đen',            100.000, 'kg',120000.00, 'VND', '2026-09-30', 'ACTIVE', NOW(), NOW()),
  -- Dùng bởi OFFERED contract
  ('lst-seed-offered',   @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b01',   'Gạo tẻ ST25',            800.000, 'kg',  8500.00, 'VND', '2026-10-01', 'ACTIVE', NOW(), NOW()),
  -- Dùng bởi NEGOTIATING contract
  ('lst-seed-neg',       @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b02', 'Cà phê Robusta',          300.000, 'kg', 60000.00, 'VND', '2026-11-01', 'ACTIVE', NOW(), NOW()),
  -- Dùng bởi SIGNED → DISPUTED contracts (đã CLOSED khi ký hợp đồng)
  ('lst-seed-signed',    @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b01',   'Gạo tẻ ST25',            500.000, 'kg',  8000.00, 'VND', '2026-10-01', 'CLOSED', NOW(), NOW()),
  ('lst-seed-active',    @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b03', 'Hồ tiêu đen',            200.000, 'kg',110000.00, 'VND', '2026-09-15', 'CLOSED', NOW(), NOW()),
  ('lst-seed-delivered', @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b02', 'Cà phê Robusta',          300.000, 'kg', 58000.00, 'VND', '2026-10-15', 'CLOSED', NOW(), NOW()),
  ('lst-seed-settled',   @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b01',   'Gạo tẻ ST25',           1000.000, 'kg',  7800.00, 'VND', '2026-08-31', 'CLOSED', NOW(), NOW()),
  ('lst-seed-cancelled', @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b04', 'Sầu riêng Musang King',  400.000, 'kg', 85000.00, 'VND', '2026-09-01', 'CLOSED', NOW(), NOW()),
  ('lst-seed-disputed',  @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b03', 'Hồ tiêu đen',            150.000, 'kg',125000.00, 'VND', '2026-09-30', 'CLOSED', NOW(), NOW()),
  -- Dùng bởi ACTIVE contract thứ 2 (seller-cancel test — cancel là terminal, cần fixture riêng với buyer-cancel)
  ('lst-seed-active-2',  @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b04', 'Sầu riêng Musang King',  250.000, 'kg', 90000.00, 'VND', '2026-09-20', 'CLOSED', NOW(), NOW());

-- Bruno e2e contract flow dùng listing này — reset ACTIVE mỗi lần seed vì bị CLOSED sau khi ký hợp đồng
INSERT INTO listings (listing_id, seller_id, product_id, product_name, quantity, quantity_unit, price_floor, currency, delivery_deadline, status, created_at, updated_at)
VALUES ('51ef2a70-95f6-4a53-bdb1-6c9ccc9becaf', @seller1_id, '7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b01', 'Gao ST25', 500.000, 'kg', 15000.00, 'VND', '2026-09-30', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE status = 'ACTIVE', updated_at = NOW();
