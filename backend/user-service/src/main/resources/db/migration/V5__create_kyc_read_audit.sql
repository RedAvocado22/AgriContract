-- Read-audit for Restricted KYC profile data (#155, data-governance-phase2-design.md §8).
-- Append-only: the application only inserts. Retention is 2 years; cleanup is a known limitation.
-- V5 because V4 belongs to #161 (ARBITRATOR role).
CREATE TABLE kyc_read_audit
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id      VARCHAR(255) NOT NULL,
    actor_role    VARCHAR(100) NOT NULL,
    resource_type VARCHAR(32)  NOT NULL,
    resource_id   VARCHAR(255) NOT NULL,
    case_context  VARCHAR(255) NULL,
    accessed_at   DATETIME(6)  NOT NULL
);

-- "Who opened profile X" and "what did staff Y open".
CREATE INDEX idx_kyc_read_audit_resource_accessed_at ON kyc_read_audit (resource_id, accessed_at);
CREATE INDEX idx_kyc_read_audit_actor_accessed_at ON kyc_read_audit (actor_id, accessed_at);
