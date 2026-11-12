-- Idempotent identity for externally scraped rows; manual entries remain append-only and unkeyed here.
ALTER TABLE price_history
    ADD COLUMN source_identity CHAR(64) NULL,
    ADD UNIQUE KEY uk_price_history_source_identity (source_identity);

CREATE TABLE IF NOT EXISTS price_ingestion_failure (
    id             CHAR(36)      NOT NULL,
    commodity      VARCHAR(20)   NOT NULL,
    failure_reason TEXT          NOT NULL,
    detected_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_price_ingestion_failure_time (detected_at)
);
