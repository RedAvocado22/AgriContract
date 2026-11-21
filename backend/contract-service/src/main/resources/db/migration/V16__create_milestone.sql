-- #119, milestone-escrow design §2.2, D63-D65: one row per delivery batch of an ACTIVE contract, its own aggregate.
-- funding_status is the funding projection (§6b); status is the delivery state (§3.2). A milestone only leaves CREATED
-- once its batch is LOCKED: the seller has no delivery obligation before that. batch_amount is fixed (rounded to the
-- whole dong) when funding is requested and is NULL before.
CREATE TABLE milestone (
    milestone_id                 VARCHAR(36)   NOT NULL,
    contract_id                  VARCHAR(255)  NOT NULL,
    milestone_index              INT           NOT NULL,
    committed_quantity           DECIMAL(10,3) NOT NULL,
    expected_delivery_date       DATE          NOT NULL,
    grace_days                   INT           NOT NULL,
    funding_scheduled_date       DATE          NOT NULL,
    status                       VARCHAR(32)   NOT NULL,
    funding_status               VARCHAR(16)   NOT NULL,
    effective_unit_price         DECIMAL(10,2) NULL,
    batch_amount                 DECIMAL(18,2) NULL,
    funding_requested_at         TIMESTAMP(6)  NULL,
    funding_locked_at            TIMESTAMP(6)  NULL,
    funding_delay_business_days  INT           NOT NULL DEFAULT 0,
    last_funding_failure_reason  VARCHAR(500)  NULL,
    created_at                   TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (milestone_id),
    CONSTRAINT uk_milestone_contract_index UNIQUE (contract_id, milestone_index),
    CONSTRAINT fk_milestone_contract FOREIGN KEY (contract_id) REFERENCES contracts (contract_id),
    CONSTRAINT chk_milestone_index CHECK (milestone_index BETWEEN 1 AND 100),
    CONSTRAINT chk_milestone_quantity CHECK (committed_quantity > 0 AND committed_quantity <= 1000000),
    CONSTRAINT chk_milestone_grace CHECK (grace_days >= 0),
    CONSTRAINT chk_milestone_schedule CHECK (funding_scheduled_date < expected_delivery_date),
    CONSTRAINT chk_milestone_status CHECK (status IN ('CREATED', 'IN_PROGRESS', 'SELLER_WEIGHED', 'BUYER_RECEIVED',
        'AWAITING_SELLER_RESPONSE', 'CONTESTED', 'FORCE_MAJEURE_PENDING_REVIEW', 'SETTLED')),
    CONSTRAINT chk_milestone_funding_status CHECK (funding_status IN ('NOT_REQUESTED', 'FUNDING_PENDING', 'LOCKED',
        'FUNDING_FAILED')),
    -- The batch is fixed exactly when funding is requested, in whole dong.
    CONSTRAINT chk_milestone_batch CHECK ((funding_status = 'NOT_REQUESTED') = (batch_amount IS NULL)
        AND (batch_amount IS NULL OR (batch_amount > 0 AND batch_amount = ROUND(batch_amount, 0)))),
    CONSTRAINT chk_milestone_requested CHECK ((funding_status = 'NOT_REQUESTED') = (funding_requested_at IS NULL)
        AND (funding_status = 'NOT_REQUESTED') = (effective_unit_price IS NULL)),
    CONSTRAINT chk_milestone_locked CHECK ((funding_status = 'LOCKED') = (funding_locked_at IS NOT NULL)),
    -- D64: no delivery obligation before the batch is locked.
    CONSTRAINT chk_milestone_obligation CHECK (status = 'CREATED' OR funding_status = 'LOCKED'),
    CONSTRAINT chk_milestone_delay CHECK (funding_delay_business_days >= 0)
);

CREATE INDEX idx_milestone_funding_due ON milestone (funding_status, funding_scheduled_date);
