-- KYC decision metadata (user-service-phase2-design.md §2, §6). Additive only.
-- verified_by_actor_id / verified_at record the latest KYC decision (verify or reject).
ALTER TABLE user_profiles
    MODIFY role ENUM('SELLER', 'BUYER', 'ADMIN', 'INSPECTOR', 'OPERATOR') NOT NULL,
    ADD COLUMN authorization_expires_at DATETIME(6) NULL,
    ADD COLUMN verified_by_actor_id     VARCHAR(255) NULL,
    ADD COLUMN verified_at              DATETIME(6) NULL,
    ADD COLUMN rejection_reason         TEXT NULL;

-- Admin KYC queue: filter by status, oldest first.
CREATE INDEX idx_user_profiles_verification_status_created_at
    ON user_profiles (verification_status, created_at);
