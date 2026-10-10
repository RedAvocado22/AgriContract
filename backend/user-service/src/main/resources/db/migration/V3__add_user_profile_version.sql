-- Optimistic lock for KYC decisions (#106): two concurrent verify/reject calls on the
-- same profile must not both succeed. Existing rows start at version 0.
ALTER TABLE user_profiles
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
