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
