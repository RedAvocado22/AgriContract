-- pricing-service — additive migration (new schema `pricing_db`, creates new tables only).
-- price_history is the source of truth for reference prices and is APPEND-ONLY:
-- the service only INSERTs and SELECTs; corrections are new rows, never UPDATE/DELETE
-- (pricing-service-phase2-design.md §2, §3.2).

CREATE TABLE IF NOT EXISTS price_history (
    id                CHAR(36)      NOT NULL,
    commodity         VARCHAR(20)   NOT NULL,
    item_name         VARCHAR(255)  NOT NULL,
    item_slug         VARCHAR(255)  NOT NULL,
    province          VARCHAR(100)  NOT NULL,
    province_slug     VARCHAR(100)  NOT NULL,
    price             DECIMAL(10,2) NOT NULL,
    source            VARCHAR(20)   NOT NULL,
    captured_at       DATE          NOT NULL,
    ingested_at       DATETIME(3)   NOT NULL,
    reference_source  VARCHAR(255)  NULL,
    reason            VARCHAR(500)  NULL,
    recorded_by       VARCHAR(255)  NULL,
    idempotency_key   VARCHAR(255)  NULL,
    request_hash      CHAR(64)      NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_price_history_commodity CHECK (commodity = 'COFFEE'),
    CONSTRAINT chk_price_history_source CHECK (source IN ('VNSAT_SCRAPE', 'ADMIN_MANUAL')),
    CONSTRAINT chk_price_history_price CHECK (price > 0),
    CONSTRAINT chk_price_history_manual_attribution CHECK (
        source <> 'ADMIN_MANUAL'
        OR (reference_source IS NOT NULL AND reason IS NOT NULL AND recorded_by IS NOT NULL)
    ),
    UNIQUE KEY uk_price_history_idempotency (recorded_by, idempotency_key),
    INDEX idx_price_history_latest (commodity, province_slug, item_slug, captured_at, ingested_at)
);
