CREATE TABLE escrow_transactions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id    VARCHAR(255)   NOT NULL UNIQUE,
    escrow_account_id BIGINT         NOT NULL,
    escrow_id         VARCHAR(255)   NOT NULL,
    transaction_type  VARCHAR(25)    NOT NULL,
    amount            DECIMAL(15,2)  NOT NULL,
    currency          VARCHAR(10)    NOT NULL,
    note              TEXT,
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_escrow_id (escrow_id),
    CONSTRAINT fk_escrow_transactions_account
        FOREIGN KEY (escrow_account_id) REFERENCES escrow_accounts (id)
);
