-- Mutable D53 projection; historical lock/override rows remain untouched.
CREATE TABLE user_lock_state (
    user_id CHAR(36) PRIMARY KEY,
    lock_revision BIGINT NOT NULL,
    effective_locked_until TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_lock_revision CHECK (lock_revision >= 0),
    INDEX idx_user_lock_state_until (effective_locked_until)
);

-- Seed the projection without publishing events; the first transition starts at revision 1.
INSERT INTO user_lock_state (user_id, lock_revision, effective_locked_until, updated_at)
SELECT l.user_id, 0, MAX(l.locked_until), CURRENT_TIMESTAMP
FROM lock_entry l
WHERE l.locked_until > CURRENT_TIMESTAMP
  AND NOT EXISTS (SELECT 1 FROM lock_override_event o WHERE o.lock_entry_id = l.entry_id)
GROUP BY l.user_id;

-- Durable business identities, independent of transport IDs and outbox retention.
-- One creation per remedy decision; one early-unlock publication per approved override.
CREATE TABLE lock_event_receipt (
    transition_type VARCHAR(20) NOT NULL,
    business_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (transition_type, business_id),
    CONSTRAINT chk_lock_transition_type CHECK (transition_type IN ('LOCK','EARLY_UNLOCK'))
);

-- Shared java-eventing outbox schema. This service uses the existing writer/poller.
CREATE TABLE event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    envelope JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    UNIQUE KEY uk_event_outbox_event_id (event_id),
    INDEX idx_event_outbox_status_id (status,id)
);
