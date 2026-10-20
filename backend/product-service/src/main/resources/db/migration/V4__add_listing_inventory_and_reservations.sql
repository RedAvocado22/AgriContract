-- Existing rows keep quantity_available = NULL (legacy): readable, but every reserve fails closed
-- until a seller edit/republish initializes it. New listings start at quantity (offeredQuantity).
ALTER TABLE listings
    ADD COLUMN quantity_available DECIMAL(15, 3) NULL AFTER quantity_unit,
    ADD CONSTRAINT chk_listings_quantity_available
        CHECK (quantity_available IS NULL OR (quantity_available >= 0 AND quantity_available <= quantity));

-- One reservation per contract: contract_id is the idempotency identity of reserve.
CREATE TABLE listing_reservations
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    contract_id       VARCHAR(255)   NOT NULL,
    listing_id        VARCHAR(255)   NOT NULL,
    reserved_quantity DECIMAL(15, 3) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        DATETIME       NOT NULL,
    updated_at        DATETIME       NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_listing_reservations_contract_id (contract_id),
    INDEX idx_listing_reservations_listing_status (listing_id, status),
    CONSTRAINT fk_listing_reservations_listing FOREIGN KEY (listing_id) REFERENCES listings (listing_id),
    CONSTRAINT chk_listing_reservations_reserved_quantity CHECK (reserved_quantity > 0),
    CONSTRAINT chk_listing_reservations_status
        CHECK (status IN ('RESERVED', 'SIGNED_RELEASABLE', 'COMMITTED', 'RELEASED'))
);
