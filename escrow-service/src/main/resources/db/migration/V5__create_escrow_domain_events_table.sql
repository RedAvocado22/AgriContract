CREATE TABLE escrow_domain_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(255) NOT NULL UNIQUE,
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload      JSON         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME,

    INDEX idx_status_created (status, created_at)
);
