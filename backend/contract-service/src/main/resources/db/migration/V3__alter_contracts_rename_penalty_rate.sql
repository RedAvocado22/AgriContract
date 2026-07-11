ALTER TABLE contracts
    CHANGE penalty_rate buyer_penalty_rate DECIMAL(10,4),
    ADD COLUMN seller_deposit_rate DECIMAL(10,4) AFTER buyer_penalty_rate;
