-- Outbox for bank.*_completed / bank.*_failed, written in the same transaction as the ledger entry.
-- Same table as backend/shared/java-eventing/src/main/resources/db/eventing/V1__create_eventing_tables.sql.
-- processed_event is not copied: bank deduplicates on ledger_entry.source_event_id (bank design §4).
CREATE TABLE event_outbox (
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
