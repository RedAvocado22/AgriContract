-- analytics-service — additive migration (new schema `analytics_db`, creates new tables only).
-- Idempotency log required by analytics-service-phase2-design.md §2.3: every ingested event is
-- recorded here in the SAME transaction as its projection write, so an at-least-once redelivery
-- or DLQ replay of the same eventId is skipped and never double-counts money.
--
-- Shape follows the shared @agricontract/eventing dedupe store: event_id is the canonical envelope
-- eventId (publishers also set it as the RabbitMQ messageId, i.e. the design's message_id), and
-- consumer_name keeps each projection's dedupe independent.
-- Fact/dimension tables arrive with their ingest issues (#133, #148).

CREATE TABLE IF NOT EXISTS analytics_idempotency_log (
    consumer_name VARCHAR(100) NOT NULL,
    event_id      CHAR(36)     NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer_name, event_id)
);
