-- file-service-phase2-design.md §8, table `file`, in MySQL 8 syntax (now() -> CURRENT_TIMESTAMP).
-- Columns, types, defaults, the deletion CHECK and the three indexes are the design's.
-- `email_intake_failure` (§8, same block) belongs to the email intake bridge (#275) and comes with it.
CREATE TABLE file (
    file_id           CHAR(36) PRIMARY KEY,
    storage_hash      VARCHAR(64) NOT NULL,   -- SHA-256 hex of the stored bytes: object-storage tampering detection
    uploaded_by       VARCHAR(255) NOT NULL,  -- userId or serviceId, plain, no cross-service FK
    ingest_channel    VARCHAR(20) NOT NULL,   -- DIRECT_UPLOAD | EMAIL_INTAKE | SYSTEM_GENERATED, set by the entrypoint
    content_type      VARCHAR(100) NOT NULL,
    file_size         BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING | READY | FAILED
    failure_reason    TEXT NULL,              -- only when status = FAILED
    attached          BOOLEAN NOT NULL DEFAULT FALSE,  -- §7, orphan cleanup
    retention_until   TIMESTAMP NULL,         -- NULL = no finite policy / keep indefinitely
    legal_hold        BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP NULL,         -- governance tombstone; blob deletion follows
    deletion_reason   TEXT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_file_deletion_metadata CHECK (
      (deleted_at IS NULL AND deletion_reason IS NULL)
      OR (deleted_at IS NOT NULL AND deletion_reason IS NOT NULL AND length(trim(deletion_reason)) > 0)
    )
);
CREATE INDEX idx_file_status ON file(status);
CREATE INDEX idx_file_attached_created ON file(attached, created_at);  -- orphan cleanup query
CREATE INDEX idx_file_retention ON file(legal_hold, retention_until, deleted_at);
