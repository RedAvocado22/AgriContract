-- ARBITRATOR staff role (SRS v1.6, data-governance-phase2-design.md §5.2, user-service-phase2-design.md §6).
-- Additive only: appends a value, existing rows and value order are untouched.
ALTER TABLE user_profiles
    MODIFY role ENUM('SELLER', 'BUYER', 'ADMIN', 'INSPECTOR', 'OPERATOR', 'ARBITRATOR') NOT NULL;
