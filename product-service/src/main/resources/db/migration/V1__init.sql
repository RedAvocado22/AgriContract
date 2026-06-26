CREATE TABLE IF NOT EXISTS products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    unit        VARCHAR(50)  NOT NULL,
    category    VARCHAR(100),
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS listings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id        VARCHAR(255)   NOT NULL UNIQUE,
    seller_id         VARCHAR(255)   NOT NULL,
    product_id        VARCHAR(255)   NOT NULL,
    product_name      VARCHAR(255)   NOT NULL,
    quantity          DECIMAL(15, 3) NOT NULL,
    quantity_unit     VARCHAR(50)    NOT NULL,
    price_floor       DECIMAL(15, 2) NOT NULL,
    currency          VARCHAR(10)    NOT NULL,
    delivery_deadline DATE           NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
