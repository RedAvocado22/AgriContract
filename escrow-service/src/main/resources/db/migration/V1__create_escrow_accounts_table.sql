CREATE TABLE escrow_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    escrow_id       VARCHAR(255)   NOT NULL UNIQUE,
    contract_id     VARCHAR(255)   NOT NULL UNIQUE,
    buyer_user_id   VARCHAR(255)   NOT NULL,
    seller_user_id  VARCHAR(255)   NOT NULL,
    total_amount    DECIMAL(15,2)  NOT NULL,
    seller_deposit  DECIMAL(15,2),
    currency        VARCHAR(10)    NOT NULL,
    status          VARCHAR(25)    NOT NULL,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_buyer_user_id  (buyer_user_id),
    INDEX idx_seller_user_id (seller_user_id),
    INDEX idx_status         (status)
);
