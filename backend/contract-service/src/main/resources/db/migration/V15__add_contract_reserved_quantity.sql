-- #232: the quantity product-service reserved for this contract at create (milestone-escrow design §2.1d), kept to
-- reconcile against the listing reservation and to carry on the lifecycle events. Same DECIMAL(15,3) as
-- product-service listing_reservations.reserved_quantity. Additive: rows created before #232 keep NULL.
ALTER TABLE contracts
    ADD COLUMN reserved_quantity DECIMAL(15, 3) NULL,
    ADD CONSTRAINT chk_contracts_reserved_quantity CHECK (reserved_quantity IS NULL OR reserved_quantity > 0);
