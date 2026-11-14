-- #433: listing statuses follow supplementary-services-api.yaml (DRAFT, AVAILABLE, PARTIALLY_COMMITTED, PAUSED,
-- CLOSED; design §8b.1), the Phase 1 delivery deadline is no longer used, and updateListing gets Idempotency-Keys.
-- Additive: no column is renamed or dropped. updated_at is not touched, so listingVersionTokens stay valid.

-- 1. Widen the enum so old and new values coexist while rows are mapped.
ALTER TABLE listings
    MODIFY COLUMN status ENUM ('ACTIVE', 'EXPIRED', 'DRAFT', 'AVAILABLE', 'PARTIALLY_COMMITTED', 'PAUSED', 'CLOSED') NOT NULL;

-- 2. ACTIVE from the quantities: nothing reserved (or legacy NULL counter) -> AVAILABLE, some reserved and stock left
--    -> PARTIALLY_COMMITTED, no stock left -> CLOSED as inventory-exhausted. Assignments run left to right on the
--    original quantity_available, which neither assignment changes.
UPDATE listings
SET status       = CASE
                       WHEN quantity_available IS NULL OR quantity_available = quantity THEN 'AVAILABLE'
                       WHEN quantity_available > 0 THEN 'PARTIALLY_COMMITTED'
                       ELSE 'CLOSED' END,
    close_reason = CASE WHEN quantity_available = 0 THEN 'INVENTORY_EXHAUSTED' ELSE close_reason END
WHERE status = 'ACTIVE';

-- 3. EXPIRED came from the removed delivery-deadline expiry; a release never reopens it.
UPDATE listings
SET status       = 'CLOSED',
    close_reason = 'LEGACY_EXPIRED'
WHERE status = 'EXPIRED';

-- 4. Only the contract values remain.
ALTER TABLE listings
    MODIFY COLUMN status ENUM ('DRAFT', 'AVAILABLE', 'PARTIALLY_COMMITTED', 'PAUSED', 'CLOSED') NOT NULL;

-- 5. Delivery dates live in the contract milestones, not the listing (team lead decision 13/11/2026). The column stays
--    for old rows; new rows leave it NULL.
ALTER TABLE listings
    MODIFY COLUMN delivery_deadline DATE NULL;

-- 6. updateListing Idempotency-Keys, unique per seller. Written in the edit's transaction, so a key exists exactly
--    when its edit committed; request_fingerprint tells a replay from a reuse with another body.
CREATE TABLE listing_edit_keys
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id           VARCHAR(255) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    listing_id          VARCHAR(255) NOT NULL,
    request_fingerprint CHAR(64)     NOT NULL,
    created_at          DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_listing_edit_keys_seller_key UNIQUE (seller_id, idempotency_key),
    CONSTRAINT fk_listing_edit_keys_listing FOREIGN KEY (listing_id) REFERENCES listings (listing_id)
);
