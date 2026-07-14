CREATE TABLE categories
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    category_id        VARCHAR(255) NOT NULL,
    name               VARCHAR(100) NOT NULL,
    normalized_name    VARCHAR(100) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    rejection_reason   VARCHAR(500),
    proposed_by        VARCHAR(255) NOT NULL,
    proposed_by_email  VARCHAR(255) NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_category_id (category_id),
    UNIQUE KEY uk_categories_normalized_name (normalized_name)
);

CREATE TABLE product_domain_events
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(255) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload      JSON         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   DATETIME     NOT NULL,
    published_at DATETIME,

    PRIMARY KEY (id),
    UNIQUE KEY uk_product_domain_events_event_id (event_id)
);

ALTER TABLE products
    ADD COLUMN category_id VARCHAR(255) AFTER unit,
    ADD COLUMN images JSON NOT NULL DEFAULT (JSON_ARRAY()) AFTER category_id,
    DROP COLUMN category;

ALTER TABLE listings
    ADD COLUMN cover_image_url VARCHAR(1000) NOT NULL DEFAULT '' AFTER product_name;
