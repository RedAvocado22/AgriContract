ALTER TABLE escrow_accounts
    ADD COLUMN seller_deposit_rate DECIMAL(5,4) AFTER seller_deposit;
