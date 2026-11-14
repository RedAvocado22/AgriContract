-- #424: createListing requires Idempotency-Key (supplementary-services-api.yaml). The key is stored on the row it
-- created, so key and listing commit in the same INSERT. Unique per seller: two sellers may use the same key.
-- Rows created before V10 keep NULL; MySQL allows any number of NULLs in a unique index.
ALTER TABLE listings
    ADD COLUMN create_idempotency_key VARCHAR(255) NULL,
    ADD CONSTRAINT uk_listings_seller_create_idempotency_key UNIQUE (seller_id, create_idempotency_key);
