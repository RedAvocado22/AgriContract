-- Copied from backend/shared/java-eventing/src/main/resources/db/eventing/V1__create_eventing_tables.sql
-- (renumbered for escrow's sequence).
-- java-eventing — additive migration (creates new tables only, touches nothing existing).
-- Same tables as backend/shared/node-eventing/migrations/V1__create_eventing_tables.sql, so every
-- service, Java or Node, has one eventing schema. Copy into the service's own Flyway folder,
-- renumbering the file to fit that service's sequence; this module never runs Flyway itself.

-- Transactional outbox: written by OutboxWriter in the same transaction as the business change,
-- published by OutboxPoller. Stores the full envelope, so a resend carries the original eventId.
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
