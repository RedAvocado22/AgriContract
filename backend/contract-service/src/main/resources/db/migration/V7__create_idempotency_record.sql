-- #93: Idempotency-Key per actor and operation, shared by every contract-service command that takes the header.
-- request_hash = SHA-256 of the request content; the same key with a different request is refused.
-- Written in the command's own transaction, so a failed command leaves no row. Rows are kept.
CREATE TABLE idempotency_record (
    actor_id        VARCHAR(255) NOT NULL,
    operation       VARCHAR(64)  NOT NULL,
    idempotency_key CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash    CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (actor_id, operation, idempotency_key),
    CONSTRAINT chk_idempotency_request_hash CHECK (request_hash REGEXP '^[a-f0-9]{64}$')
);
