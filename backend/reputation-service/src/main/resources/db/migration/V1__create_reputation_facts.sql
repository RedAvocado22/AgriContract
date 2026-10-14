CREATE TABLE completed_contract_fact (
    contract_id CHAR(36) PRIMARY KEY,
    buyer_id CHAR(36) NOT NULL,
    seller_id CHAR(36) NOT NULL,
    settled_at DATETIME(6) NOT NULL,
    source_event_id CHAR(36) NOT NULL UNIQUE,
    INDEX idx_completed_buyer (buyer_id),
    INDEX idx_completed_seller (seller_id)
);
CREATE TABLE dispute_outcome_fact (
    milestone_id CHAR(36) PRIMARY KEY,
    contract_id CHAR(36) NOT NULL,
    flagged_by_user_id CHAR(36) NOT NULL,
    resolution_favors VARCHAR(10) NOT NULL,
    resolved_at DATETIME(6) NOT NULL,
    source_event_id CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT chk_dispute_resolution CHECK (resolution_favors IN ('BUYER', 'SELLER')),
    INDEX idx_dispute_flagger_outcome (flagged_by_user_id, resolution_favors)
);
