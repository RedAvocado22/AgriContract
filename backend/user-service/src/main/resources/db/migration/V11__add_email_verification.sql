-- Email ownership proof for the register email (#344, user-service-phase2-design.md §4.4). Additive only.
-- V10 is taken by role_change_audit outcome (#261); merge that first.

-- verified_email is set only after the user confirms an emailed code. The unique key gives each address one proven
-- owner (case-insensitive under the default collation); NULLs do not collide, so unproven duplicates may coexist.
-- Existing rows stay unverified: since #314 their email came from the request body and was never proven.
ALTER TABLE user_profiles
    ADD COLUMN verified_email    VARCHAR(255) NULL,
    ADD COLUMN email_verified_at DATETIME(6)  NULL,
    ADD CONSTRAINT uk_user_profiles_verified_email UNIQUE (verified_email);

-- At most one pending code per user; a new request replaces the row, a confirmed code deletes it.
-- code_hash is HMAC-SHA256(code, pepper) in hex; the plain code is never stored.
CREATE TABLE email_verification_challenge (
    user_id       VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    code_hash     CHAR(64)     NOT NULL,
    sent_at       DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    attempts_left INT          NOT NULL,
    PRIMARY KEY (user_id)
);
