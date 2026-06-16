CREATE TABLE IF NOT EXISTS products (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    product_id  VARCHAR(255)    NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    unit        VARCHAR(50)     NOT NULL,
    category    VARCHAR(100),
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listings (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    listing_id          VARCHAR(255)    NOT NULL,
    seller_id           VARCHAR(255)    NOT NULL,
    product_id          VARCHAR(255)    NOT NULL,
    product_name        VARCHAR(255)    NOT NULL,
    quantity            DECIMAL(15,3)   NOT NULL,
    quantity_unit       VARCHAR(50)     NOT NULL,
    price_floor         DECIMAL(15,2)   NOT NULL,
    currency            VARCHAR(10)     NOT NULL,
    delivery_deadline   DATE            NOT NULL,
    status              ENUM('ACTIVE','CLOSED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_listings_listing_id (listing_id),
    INDEX idx_listings_seller_id (seller_id),
    INDEX idx_listings_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
