-- ==============================================================
-- DEV SEED: escrow_db — 6 escrow accounts (SIGNED → DISPUTED)
--
-- Amounts khớp đúng business logic EscrowAccount.java:
--   BUYER_LOCKED  : 1 tx LOCK (buyer)
--   FULLY_LOCKED  : 2 tx LOCK (buyer + seller)
--   RELEASED      : + RELEASE + REFUND_TO_SELLER
--   PENALIZED_BUYER: + PENALIZE_BUYER + REFUND_TO_BUYER + REFUND_TO_SELLER
--
-- Cập nhật 4 biến dưới đây trước khi chạy
-- ==============================================================

SET @buyer1_id    = '1de004cd-8493-463c-8f32-6c7b75bcbf16';
SET @buyer1_email = 'buyer1@test.com';
SET @seller1_id   = '1a5a2ddd-56b9-492a-b966-99f436e0fc85';
SET @seller1_email = 'seller1@test.com';

-- ── Escrow Accounts ──────────────────────────────────────────

INSERT IGNORE INTO escrow_accounts
  (escrow_id, contract_id, buyer_user_id, buyer_email, seller_user_id, seller_email,
   total_amount, seller_deposit, seller_deposit_rate, currency, status, created_at, updated_at)
VALUES
  -- SIGNED contract: buyer locked 4,100,000 (500kg × 8200), seller chưa lock
  ('seed-esc-signed',    'seed-ctr-signed',
   @buyer1_id, @buyer1_email, @seller1_id, @seller1_email,
   4100000.00, NULL, 0.1000, 'VND', 'BUYER_LOCKED', NOW(), NOW()),

  -- ACTIVE contract: cả hai locked — total 22,000,000 (200kg × 110000), deposit 2,200,000
  ('seed-esc-active',    'seed-ctr-active',
   @buyer1_id, @buyer1_email, @seller1_id, @seller1_email,
   22000000.00, 2200000.00, 0.1000, 'VND', 'FULLY_LOCKED', NOW(), NOW()),

  -- DELIVERED contract: FULLY_LOCKED, chờ contract-service nhận escrow.released
  -- total 17,400,000 (300kg × 58000), deposit 1,740,000
  ('seed-esc-delivered', 'seed-ctr-delivered',
   @buyer1_id, @buyer1_email, @seller1_id, @seller1_email,
   17400000.00, 1740000.00, 0.1000, 'VND', 'FULLY_LOCKED', NOW(), NOW()),

  -- SETTLED contract: RELEASED — total 7,800,000 (1000kg × 7800), deposit 780,000
  ('seed-esc-settled',   'seed-ctr-settled',
   @buyer1_id, @buyer1_email, @seller1_id, @seller1_email,
   7800000.00, 780000.00, 0.1000, 'VND', 'RELEASED', NOW(), NOW()),

  -- CANCELLED contract (buyer fault): PENALIZED_BUYER
  -- total 34,000,000 (400kg × 85000), deposit 3,400,000, penalty = 34M × 0.2 = 6,800,000
  ('seed-esc-cancelled', 'seed-ctr-cancelled',
   @buyer1_id, @buyer1_email, @seller1_id, @seller1_email,
   34000000.00, 3400000.00, 0.1000, 'VND', 'PENALIZED_BUYER', NOW(), NOW()),

  -- DISPUTED contract: FULLY_LOCKED, chờ Admin arbitrate
  -- total 18,750,000 (150kg × 125000), deposit 1,875,000
  ('seed-esc-disputed',  'seed-ctr-disputed',
   @buyer1_id, @buyer1_email, @seller1_id, @seller1_email,
   18750000.00, 1875000.00, 0.1000, 'VND', 'FULLY_LOCKED', NOW(), NOW());

-- ── Escrow Transactions ───────────────────────────────────────
-- Dùng subquery lấy escrow_account_id từ escrow_id (không hardcode AUTO_INCREMENT id)
-- transaction_id phải là UUID — EscrowMapper.toDomain() gọi UUID.fromString()

-- BUYER_LOCKED: 1 tx
INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '0e328c0f-45d5-48c1-aa3d-6c3f306ac900', id, 'seed-esc-signed', 'LOCK', 4100000.00, 'VND', 'Lock buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-signed';

-- FULLY_LOCKED (active): 2 tx
INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT 'a2085d2a-24d9-4826-ba4c-27be3889fafb', id, 'seed-esc-active', 'LOCK', 22000000.00, 'VND', 'Lock buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-active';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '47e21c1e-dfb6-4c04-9a8c-fb31e94de536', id, 'seed-esc-active', 'LOCK', 2200000.00, 'VND', 'Lock seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-active';

-- FULLY_LOCKED (delivered): 2 tx
INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '35e001b9-ba5b-4098-9fa7-e1365a6c289a', id, 'seed-esc-delivered', 'LOCK', 17400000.00, 'VND', 'Lock buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-delivered';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '9d076632-c0d5-4029-b58e-f9e38a17f0ce', id, 'seed-esc-delivered', 'LOCK', 1740000.00, 'VND', 'Lock seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-delivered';

-- RELEASED (settled): 4 tx — LOCK x2, RELEASE, REFUND_TO_SELLER
INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '1fbc66ce-c2a1-43ab-8a70-83004e13f51c', id, 'seed-esc-settled', 'LOCK', 7800000.00, 'VND', 'Lock buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-settled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '1b8201f6-8fda-4002-bd73-f09bf3a4f637', id, 'seed-esc-settled', 'LOCK', 780000.00, 'VND', 'Lock seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-settled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '19ded999-5f6a-4fea-8b34-f92834d582bf', id, 'seed-esc-settled', 'RELEASE', 7800000.00, 'VND', 'Release buyer payment to seller.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-settled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT 'f7e50a69-6a5d-4899-bc96-1836c67b236c', id, 'seed-esc-settled', 'REFUND_TO_SELLER', 780000.00, 'VND', 'Refund seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-settled';

-- PENALIZED_BUYER (cancelled): 5 tx — LOCK x2, PENALIZE_BUYER, REFUND_TO_BUYER, REFUND_TO_SELLER
-- penalty = 34,000,000 × 0.2 = 6,800,000 | refund_buyer = 34,000,000 − 6,800,000 = 27,200,000
INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '417ed552-6ff5-413d-b510-7ad58f4a44bc', id, 'seed-esc-cancelled', 'LOCK', 34000000.00, 'VND', 'Lock buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-cancelled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '1b9a6a63-99e7-46e9-bc3a-e26ab8adab7e', id, 'seed-esc-cancelled', 'LOCK', 3400000.00, 'VND', 'Lock seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-cancelled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT 'e945fe1b-9414-4624-acfd-8434b411be83', id, 'seed-esc-cancelled', 'PENALIZE_BUYER', 6800000.00, 'VND', 'Penalize buyer payment to seller.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-cancelled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT 'c750026f-477c-444f-a280-aa6822e8f10f', id, 'seed-esc-cancelled', 'REFUND_TO_BUYER', 27200000.00, 'VND', 'Refund buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-cancelled';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '9b003f4b-02d9-4f73-a5fe-4cd8555ebdb4', id, 'seed-esc-cancelled', 'REFUND_TO_SELLER', 3400000.00, 'VND', 'Refund seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-cancelled';

-- FULLY_LOCKED (disputed): 2 tx — chờ Admin gọi PUT /arbitrate
INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT 'a493caac-a940-408f-aa65-c8c98976bc86', id, 'seed-esc-disputed', 'LOCK', 18750000.00, 'VND', 'Lock buyer payment.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-disputed';

INSERT IGNORE INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type, amount, currency, note, created_at)
SELECT '18565f65-3931-4176-9d58-8161b3309eda', id, 'seed-esc-disputed', 'LOCK', 1875000.00, 'VND', 'Lock seller deposit.', NOW()
FROM escrow_accounts WHERE escrow_id = 'seed-esc-disputed';
