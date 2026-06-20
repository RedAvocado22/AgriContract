ALTER TABLE escrow_accounts
    ADD COLUMN buyer_email VARCHAR(255) NOT NULL AFTER buyer_user_id,
    ADD COLUMN seller_email VARCHAR(255) NOT NULL AFTER seller_user_id;
