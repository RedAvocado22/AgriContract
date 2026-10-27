-- Durable dedup store for contract lifecycle events, keyed by the envelope eventId.
-- The orphan reservation reaper reads it too: a reservation whose contract.created is recorded here
-- is attached to a persisted contract and is never an orphan.
CREATE TABLE processed_contract_events
(
    event_id     VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(40)  NOT NULL,
    contract_id  VARCHAR(255) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,

    PRIMARY KEY (event_id),
    INDEX idx_processed_contract_events_contract_type (contract_id, event_type)
);

-- Reaper scan: RESERVED rows older than the grace period.
CREATE INDEX idx_listing_reservations_status_created ON listing_reservations (status, created_at);
