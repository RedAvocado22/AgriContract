-- min_order_quantity NULL = no seller minimum (rows created before the field existed).
-- close_reason marks a listing closed by inventory exhaustion, the only kind of close a release may reopen;
-- any other close keeps NULL so seller/contract closes are never undone by a restore.
-- updated_at backs the opaque listingVersionToken, so it needs sub-second precision to tell edits apart.
ALTER TABLE listings
    ADD COLUMN min_order_quantity DECIMAL(15, 3) NULL AFTER quantity_available,
    ADD COLUMN close_reason VARCHAR(30) NULL AFTER status,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT chk_listings_min_order_quantity
        CHECK (min_order_quantity IS NULL OR (min_order_quantity > 0 AND min_order_quantity <= quantity));
