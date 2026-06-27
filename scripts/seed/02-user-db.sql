-- ==============================================================
-- DEV SEED: user_db
-- Cập nhật 4 biến dưới đây trước khi chạy:
--   http://localhost:8180 → realm agricontract → Users → UUID của từng account
-- ==============================================================

SET @buyer1_id    = '1de004cd-8493-463c-8f32-6c7b75bcbf16';
SET @buyer1_email = 'buyer1@test.com';
SET @seller1_id   = '1a5a2ddd-56b9-492a-b966-99f436e0fc85';
SET @seller1_email = 'seller1@test.com';

INSERT IGNORE INTO user_profiles (user_id, organization_name, role, email, phone, address, verification_status, created_at, updated_at)
VALUES
  (@buyer1_id,  'Công ty TNHH Mua Nông Sản Tám', 'BUYER',  @buyer1_email,  '0901234567', '123 Lê Lợi, Q1, TP.HCM',                'VERIFIED', NOW(), NOW()),
  (@seller1_id, 'HTX Nông Nghiệp Xanh Cần Thơ',  'SELLER', @seller1_email, '0907654321', '456 Hùng Vương, Ninh Kiều, Cần Thơ',    'VERIFIED', NOW(), NOW());
