ALTER TABLE escrow_accounts
    ADD COLUMN release_eligible_at DATETIME(6) NULL AFTER status,
    ADD INDEX idx_escrow_release_due (status, release_eligible_at);
