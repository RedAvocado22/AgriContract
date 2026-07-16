ALTER TABLE contracts
    ADD COLUMN terms_revision INT NOT NULL DEFAULT 1 AFTER signatories;

CREATE TABLE contract_negotiations (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id         VARCHAR(255)  NOT NULL,
    terms_revision      INT           NOT NULL,
    proposed_by         VARCHAR(255)  NOT NULL,
    proposed_at         DATETIME(6)   NOT NULL,
    quantity            DECIMAL(15,3) NOT NULL,
    quantity_unit       VARCHAR(50)   NOT NULL,
    agreed_price        DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(10)   NOT NULL,
    delivery_deadline   DATE          NOT NULL,
    buyer_penalty_rate  DECIMAL(10,4),
    seller_deposit_rate DECIMAL(10,4),
    quality_spec        TEXT,

    CONSTRAINT uq_contract_negotiation_revision UNIQUE (contract_id, terms_revision),
    CONSTRAINT fk_contract_negotiation_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(contract_id) ON DELETE CASCADE,
    INDEX idx_contract_negotiation_contract (contract_id, terms_revision)
);

INSERT INTO contract_negotiations (
    contract_id, terms_revision, proposed_by, proposed_at,
    quantity, quantity_unit, agreed_price, currency, delivery_deadline,
    buyer_penalty_rate, seller_deposit_rate, quality_spec
)
SELECT
    contract_id, 1, buyer_id, created_at,
    quantity, quantity_unit, agreed_price, currency, delivery_deadline,
    buyer_penalty_rate, seller_deposit_rate, quality_spec
FROM contracts;
