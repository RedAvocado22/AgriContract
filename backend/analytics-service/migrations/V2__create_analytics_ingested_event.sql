-- analytics-service — additive migration (creates a new table only).
-- Durable landing table for every validated canonical event (issue #118). It is written in the SAME
-- transaction as the analytics_idempotency_log row, so an event is acked only after it is stored and
-- deduplicated; projections (#133, #148) can be built and rebuilt from it. No contact PII is stored:
-- recipient e-mail addresses are removed from the payload before it reaches this table.

CREATE TABLE IF NOT EXISTS analytics_ingested_event (
    event_id       CHAR(36)     NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    event_version  INT          NOT NULL,
    producer       VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NULL,
    causation_id   VARCHAR(255) NULL,
    occurred_at    DATETIME(3)  NOT NULL,
    payload        JSON         NOT NULL,
    received_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id),
    INDEX idx_ingested_event_type_time (event_type, occurred_at),
    INDEX idx_ingested_event_aggregate (aggregate_id)
);
