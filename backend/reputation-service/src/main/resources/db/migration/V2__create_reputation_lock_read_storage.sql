CREATE TABLE lock_entry (
    entry_id CHAR(36) PRIMARY KEY,
    source_event_id CHAR(36) NOT NULL UNIQUE,
    remedy_decision_id CHAR(36) NOT NULL UNIQUE,
    contract_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    penalized_role VARCHAR(10) NOT NULL,
    base_days INT NOT NULL DEFAULT 30,
    repeat_offense_multiplier DECIMAL(3,2) NOT NULL,
    track_record_multiplier DECIMAL(3,2) NOT NULL,
    zero_progress_multiplier DECIMAL(3,2) NOT NULL,
    lock_duration_days INT NOT NULL,
    locked_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_lock_role CHECK (penalized_role IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_lock_duration CHECK (lock_duration_days > 0),
    INDEX idx_lock_entry_user (user_id)
);
CREATE TABLE lock_override_event (
    event_id CHAR(36) PRIMARY KEY,
    lock_entry_id CHAR(36) NOT NULL,
    override_type VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    proposed_by CHAR(36) NOT NULL,
    approved_by CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_override_lock FOREIGN KEY (lock_entry_id) REFERENCES lock_entry(entry_id),
    CONSTRAINT chk_override_type CHECK (override_type = 'UNLOCK_EARLY'),
    CONSTRAINT chk_override_actors CHECK (proposed_by <> approved_by),
    INDEX idx_override_lock (lock_entry_id)
);
