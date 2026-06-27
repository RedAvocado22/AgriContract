-- ==============================================================
-- DEV SEED: contract_db — 8 contracts bao phủ mọi state
--   buyer_penalty_rate = 0.20 (20%), seller_deposit_rate = 0.10 (10%)
-- Cập nhật 4 biến dưới đây trước khi chạy
-- ==============================================================

SET @buyer1_id    = '1de004cd-8493-463c-8f32-6c7b75bcbf16';
SET @buyer1_email = 'buyer1@test.com';
SET @buyer1_org   = 'Công ty TNHH Mua Nông Sản Tám';
SET @seller1_id   = '1a5a2ddd-56b9-492a-b966-99f436e0fc85';
SET @seller1_email = 'seller1@test.com';
SET @seller1_org  = 'HTX Nông Nghiệp Xanh Cần Thơ';

INSERT IGNORE INTO contracts
  (contract_id, listing_id, seller_id, buyer_id,
   product_name, buyer_org_name, seller_org_name, buyer_email, seller_email,
   quantity, quantity_unit, agreed_price, currency, delivery_deadline,
   buyer_penalty_rate, seller_deposit_rate, quality_spec,
   status, cancel_reason, cancelled_by, signatories,
   created_at, updated_at)
VALUES
  -- 1. OFFERED — buyer tạo offer, chưa ai ký
  ('seed-ctr-offered', 'lst-seed-offered', @seller1_id, @buyer1_id,
   'Gạo tẻ ST25', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   800.000, 'kg', 8000.00, 'VND', '2026-10-01',
   0.2000, 0.1000, 'Độ ẩm ≤ 14%, tạp chất ≤ 0.5%',
   'OFFERED', NULL, NULL, '[]',
   NOW(), NOW()),

  -- 2. NEGOTIATING — seller đã counter-offer, đang thương lượng
  ('seed-ctr-negotiating', 'lst-seed-neg', @seller1_id, @buyer1_id,
   'Cà phê Robusta', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   300.000, 'kg', 58000.00, 'VND', '2026-11-01',
   0.2000, 0.1000, 'Độ ẩm ≤ 12.5%, hạt lỗi ≤ 5%',
   'NEGOTIATING', NULL, NULL, '[]',
   NOW(), NOW()),

  -- 3. SIGNED — cả hai ký, buyer đã lock escrow (BUYER_LOCKED), chờ seller confirm deposit
  ('seed-ctr-signed', 'lst-seed-signed', @seller1_id, @buyer1_id,
   'Gạo tẻ ST25', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   500.000, 'kg', 8200.00, 'VND', '2026-10-01',
   0.2000, 0.1000, 'Độ ẩm ≤ 14%, tạp chất ≤ 0.5%',
   'SIGNED', NULL, NULL, CONCAT('["', @buyer1_id, '","', @seller1_id, '"]'),
   NOW(), NOW()),

  -- 4. ACTIVE — escrow FULLY_LOCKED, hợp đồng đang thực hiện
  ('seed-ctr-active', 'lst-seed-active', @seller1_id, @buyer1_id,
   'Hồ tiêu đen', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   200.000, 'kg', 110000.00, 'VND', '2026-09-15',
   0.2000, 0.1000, 'Hàm lượng piperin ≥ 5%, độ ẩm ≤ 13%',
   'ACTIVE', NULL, NULL, CONCAT('["', @buyer1_id, '","', @seller1_id, '"]'),
   NOW(), NOW()),

  -- 5. DELIVERED — buyer đã confirm nhận hàng, chờ escrow release
  ('seed-ctr-delivered', 'lst-seed-delivered', @seller1_id, @buyer1_id,
   'Cà phê Robusta', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   300.000, 'kg', 58000.00, 'VND', '2026-10-15',
   0.2000, 0.1000, 'Độ ẩm ≤ 12.5%, hạt lỗi ≤ 5%',
   'DELIVERED', NULL, NULL, CONCAT('["', @buyer1_id, '","', @seller1_id, '"]'),
   NOW(), NOW()),

  -- 6. SETTLED — hoàn tất, escrow đã release cho seller
  --   total = 1000 * 7800 = 7,800,000 VND
  ('seed-ctr-settled', 'lst-seed-settled', @seller1_id, @buyer1_id,
   'Gạo tẻ ST25', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   1000.000, 'kg', 7800.00, 'VND', '2026-08-31',
   0.2000, 0.1000, 'Độ ẩm ≤ 14%, tạp chất ≤ 0.5%',
   'SETTLED', NULL, NULL, CONCAT('["', @buyer1_id, '","', @seller1_id, '"]'),
   NOW(), NOW()),

  -- 7. CANCELLED — buyer huỷ từ ACTIVE, chịu phạt 20%
  --   total = 400 * 85000 = 34,000,000 VND
  ('seed-ctr-cancelled', 'lst-seed-cancelled', @seller1_id, @buyer1_id,
   'Sầu riêng Musang King', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   400.000, 'kg', 85000.00, 'VND', '2026-09-01',
   0.2000, 0.1000, 'Trái ≥ 3kg, múi vàng nhạt, không sâu',
   'CANCELLED', 'Buyer không thu xếp được vốn đúng hạn', 'BUYER',
   CONCAT('["', @buyer1_id, '","', @seller1_id, '"]'),
   NOW(), NOW()),

  -- 8. DISPUTED — buyer dispute sau khi nhận hàng không đạt chất lượng
  --   total = 150 * 125000 = 18,750,000 VND
  ('seed-ctr-disputed', 'lst-seed-disputed', @seller1_id, @buyer1_id,
   'Hồ tiêu đen', @buyer1_org, @seller1_org, @buyer1_email, @seller1_email,
   150.000, 'kg', 125000.00, 'VND', '2026-09-30',
   0.2000, 0.1000, 'Hàm lượng piperin ≥ 5%, độ ẩm ≤ 13%',
   'DISPUTED', NULL, NULL, CONCAT('["', @buyer1_id, '","', @seller1_id, '"]'),
   NOW(), NOW());
