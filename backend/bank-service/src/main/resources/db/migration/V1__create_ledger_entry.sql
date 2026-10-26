-- Append-only money ledger (bank-service-phase2-design.md §2, §2b, §6).
-- Rows are never updated or deleted: the runtime account only has SELECT, INSERT (ops/grants.sql).
CREATE TABLE ledger_entry (
    entry_id           CHAR(36)      NOT NULL PRIMARY KEY,
    source_event_id    CHAR(36)      NOT NULL,
    contract_id        CHAR(36)      NOT NULL,
    milestone_id       CHAR(36)      NULL,
    user_id            CHAR(36)      NOT NULL,
    entry_type         VARCHAR(30)   NOT NULL,
    fund_type          VARCHAR(30)   NOT NULL,
    remedy_decision_id CHAR(36)      NULL,
    remedy_leg_id      CHAR(36)      NULL,
    amount             DECIMAL(18,2) NOT NULL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_ledger_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_ledger_remedy_leg UNIQUE (remedy_leg_id),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN (
        'LOCK_BUYER_DEPOSIT', 'LOCK_SELLER_DEPOSIT', 'LOCK_MILESTONE',
        'RELEASE_TO_SELLER', 'REFUND_TO_BUYER',
        'DEPOSIT_FORFEITURE', 'CONTRACTUAL_PENALTY', 'DAMAGES_COMPENSATION')),
    CONSTRAINT chk_ledger_fund_type CHECK (fund_type IN ('BUYER_DEPOSIT', 'SELLER_DEPOSIT', 'MILESTONE_PAYMENT')),
    -- Which pool each entry type may move (milestone-escrow §6.7). Mirrors EntryType.
    CONSTRAINT chk_ledger_entry_fund CHECK (
        (entry_type = 'LOCK_BUYER_DEPOSIT' AND fund_type = 'BUYER_DEPOSIT')
        OR (entry_type = 'LOCK_SELLER_DEPOSIT' AND fund_type = 'SELLER_DEPOSIT')
        OR (entry_type = 'LOCK_MILESTONE' AND fund_type = 'MILESTONE_PAYMENT')
        OR (entry_type = 'RELEASE_TO_SELLER' AND fund_type IN ('MILESTONE_PAYMENT', 'SELLER_DEPOSIT'))
        OR (entry_type = 'REFUND_TO_BUYER' AND fund_type IN ('MILESTONE_PAYMENT', 'BUYER_DEPOSIT'))
        OR (entry_type = 'DEPOSIT_FORFEITURE' AND fund_type IN ('BUYER_DEPOSIT', 'SELLER_DEPOSIT'))
        OR (entry_type IN ('CONTRACTUAL_PENALTY', 'DAMAGES_COMPENSATION') AND fund_type = 'MILESTONE_PAYMENT')),
    -- Deposits are contract-level; a batch belongs to exactly one milestone.
    CONSTRAINT chk_ledger_fund_scope CHECK ((fund_type = 'MILESTONE_PAYMENT') = (milestone_id IS NOT NULL)),
    -- Locks are never remedy legs; forfeiture/penalty/damages only exist through a remedy decision.
    CONSTRAINT chk_ledger_remedy_link CHECK (
        (entry_type NOT IN ('LOCK_BUYER_DEPOSIT', 'LOCK_SELLER_DEPOSIT', 'LOCK_MILESTONE') OR remedy_leg_id IS NULL)
        AND (entry_type NOT IN ('DEPOSIT_FORFEITURE', 'CONTRACTUAL_PENALTY', 'DAMAGES_COMPENSATION')
             OR remedy_leg_id IS NOT NULL)),
    -- A remedy leg always belongs to a decision, and a decision is only recorded through its legs.
    CONSTRAINT chk_ledger_remedy_pair CHECK ((remedy_decision_id IS NULL) = (remedy_leg_id IS NULL)),
    INDEX idx_ledger_entry_contract (contract_id, milestone_id),
    INDEX idx_ledger_entry_user (user_id)
);
