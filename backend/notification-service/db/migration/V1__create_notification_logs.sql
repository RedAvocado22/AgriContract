CREATE TABLE notification_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    notification_id VARCHAR(36)     NOT NULL,
    event_id        VARCHAR(36)     NOT NULL,
    user_id         VARCHAR(255)    NOT NULL,
    channel         VARCHAR(20)     NOT NULL,
    subject         VARCHAR(500)    NOT NULL,
    body            TEXT            NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_id (notification_id),
    UNIQUE KEY uq_event_user (event_id, user_id)
);
