-- @agricontract/eventing — additive migration (creates new tables only, touches nothing existing).
-- Copy into each Node service's own migration folder (each service owns its own MySQL 8 schema),
-- renumbering the file to fit that service's sequence.

-- Transactional outbox: written in the same transaction as the business change, published by OutboxPoller.
-- Mirrors the Java `<service>_domain_events` tables, but stores the full canonical envelope.
CREATE TABLE IF NOT EXISTS event_outbox (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    event_id     CHAR(36)      NOT NULL,
    event_type   VARCHAR(100)  NOT NULL,
    aggregate_id VARCHAR(255)  NOT NULL,
    envelope     JSON          NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts     INT           NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000) NULL,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_event_id (event_id),
    INDEX idx_event_outbox_status_id (status, id)
);

-- Consumer dedupe: one row per (consumer, eventId) actually processed. Replaying the original
-- eventId (redelivery or DLQ replay) hits the primary key and is skipped (Verification Matrix #7).
CREATE TABLE IF NOT EXISTS processed_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id      CHAR(36)     NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer_name, event_id)
);
