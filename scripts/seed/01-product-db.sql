-- ==============================================================
-- DEV SEED: product_db
-- Update @seller1_id before running if Keycloak user IDs change.
-- ==============================================================

SET @seller1_id = '1a5a2ddd-56b9-492a-b966-99f436e0fc85';

SET @img_rice = 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=1200&q=80';
SET @img_coffee = 'https://images.unsplash.com/photo-1447933601403-0c6688de566e?auto=format&fit=crop&w=1200&q=80';
SET @img_pepper = 'https://images.unsplash.com/photo-1532336414038-cf19250c5757?auto=format&fit=crop&w=1200&q=80';
SET @img_durian = 'https://images.unsplash.com/photo-1619566636858-adf3ef46400b?auto=format&fit=crop&w=1200&q=80';

-- Approved default categories used by the seller listing form.
INSERT IGNORE INTO categories
  (category_id, name, normalized_name, status, rejection_reason, proposed_by, proposed_by_email, created_at, updated_at)
VALUES
  ('RICE', 'Rice and grains', 'rice_and_grains', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('COFFEE', 'Coffee', 'coffee', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('CASHEW', 'Cashew', 'cashew', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('RUBBER', 'Rubber', 'rubber', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('FRUIT', 'Fruit', 'fruit', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('VEGETABLE', 'Vegetables', 'vegetables', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('SPICE', 'Spices', 'spices', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW()),
  ('OTHER', 'Other', 'other', 'APPROVED', NULL, 'system', 'system@agricontract.local', NOW(), NOW());

-- Products.
INSERT INTO products (product_id, name, unit, category_id, images, created_at, updated_at)
VALUES
  ('prod-seed-rice',   'Gạo tẻ ST25',          'kg', 'RICE',   JSON_ARRAY(@img_rice),   NOW(), NOW()),
  ('prod-seed-coffee', 'Cà phê Robusta',       'kg', 'COFFEE', JSON_ARRAY(@img_coffee), NOW(), NOW()),
  ('prod-seed-pepper', 'Hồ tiêu đen',          'kg', 'SPICE',  JSON_ARRAY(@img_pepper), NOW(), NOW()),
  ('prod-seed-durian', 'Sầu riêng Musang King','kg', 'FRUIT',  JSON_ARRAY(@img_durian), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  unit = VALUES(unit),
  category_id = VALUES(category_id),
  images = VALUES(images),
  updated_at = NOW();

-- Listings.
INSERT INTO listings
  (listing_id, seller_id, product_id, product_name, cover_image_url, quantity, quantity_unit, price_floor, currency, delivery_deadline, status, created_at, updated_at)
VALUES
  ('lst-seed-browse-01', @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',           @img_rice,   1000.000, 'kg',   8000.00, 'VND', '2026-10-15', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-browse-02', @seller1_id, 'prod-seed-coffee', 'Cà phê Robusta',        @img_coffee,  500.000, 'kg',  55000.00, 'VND', '2026-11-30', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-browse-03', @seller1_id, 'prod-seed-pepper', 'Hồ tiêu đen',           @img_pepper,  100.000, 'kg', 120000.00, 'VND', '2026-09-30', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-offered',   @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',           @img_rice,    800.000, 'kg',   8500.00, 'VND', '2026-10-01', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-neg',       @seller1_id, 'prod-seed-coffee', 'Cà phê Robusta',        @img_coffee,  300.000, 'kg',  60000.00, 'VND', '2026-11-01', 'ACTIVE', NOW(), NOW()),
  ('lst-seed-signed',    @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',           @img_rice,    500.000, 'kg',   8000.00, 'VND', '2026-10-01', 'CLOSED', NOW(), NOW()),
  ('lst-seed-active',    @seller1_id, 'prod-seed-pepper', 'Hồ tiêu đen',           @img_pepper,  200.000, 'kg', 110000.00, 'VND', '2026-09-15', 'CLOSED', NOW(), NOW()),
  ('lst-seed-delivered', @seller1_id, 'prod-seed-coffee', 'Cà phê Robusta',        @img_coffee,  300.000, 'kg',  58000.00, 'VND', '2026-10-15', 'CLOSED', NOW(), NOW()),
  ('lst-seed-settled',   @seller1_id, 'prod-seed-rice',   'Gạo tẻ ST25',           @img_rice,   1000.000, 'kg',   7800.00, 'VND', '2026-08-31', 'CLOSED', NOW(), NOW()),
  ('lst-seed-cancelled', @seller1_id, 'prod-seed-durian', 'Sầu riêng Musang King', @img_durian,  400.000, 'kg',  85000.00, 'VND', '2026-09-01', 'CLOSED', NOW(), NOW()),
  ('lst-seed-disputed',  @seller1_id, 'prod-seed-pepper', 'Hồ tiêu đen',           @img_pepper,  150.000, 'kg', 125000.00, 'VND', '2026-09-30', 'CLOSED', NOW(), NOW()),
  ('lst-seed-active-2',  @seller1_id, 'prod-seed-durian', 'Sầu riêng Musang King', @img_durian,  250.000, 'kg',  90000.00, 'VND', '2026-09-20', 'CLOSED', NOW(), NOW()),
  ('51ef2a70-95f6-4a53-bdb1-6c9ccc9becaf', @seller1_id, 'prod-seed-rice', 'Gạo tẻ ST25', @img_rice, 500.000, 'kg', 15000.00, 'VND', '2026-09-30', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  seller_id = VALUES(seller_id),
  product_id = VALUES(product_id),
  product_name = VALUES(product_name),
  cover_image_url = VALUES(cover_image_url),
  quantity = VALUES(quantity),
  quantity_unit = VALUES(quantity_unit),
  price_floor = VALUES(price_floor),
  currency = VALUES(currency),
  delivery_deadline = VALUES(delivery_deadline),
  status = VALUES(status),
  updated_at = NOW();
