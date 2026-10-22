-- analytics-service — additive migration (creates new tables only), issue #133.
-- Read models from analytics-service-phase2-design.md §2.1, §2.2 and §2.4.
-- Differences from the design sketch, on purpose:
--  * money is DECIMAL(18,2), the platform standard (the design sketch had (15,2));
--  * timestamps are DATETIME(3) in UTC (the pool runs in UTC) instead of TIMESTAMP;
--  * fact_milestone_performance has UNIQUE (milestone_id): a milestone has one final outcome, so a replayed
--    outcome can never be counted twice, even if it arrives under a new event id.

CREATE TABLE IF NOT EXISTS dim_contract (
    contract_id  CHAR(36)      NOT NULL,
    commodity    VARCHAR(50)   NOT NULL,
    buyer_id     CHAR(36)      NOT NULL,
    seller_id    CHAR(36)      NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    signed_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (contract_id)
);

CREATE TABLE IF NOT EXISTS fact_milestone_performance (
    fact_id                CHAR(36)      NOT NULL,
    contract_id            CHAR(36)      NOT NULL,
    milestone_id           CHAR(36)      NOT NULL,
    status                 VARCHAR(30)   NOT NULL, -- SETTLED / CANCELLED_WITH_PENALTY
    locked_amount          DECIMAL(18,2) NOT NULL,
    actual_amount          DECIMAL(18,2) NOT NULL,
    delta_shortfall_amount DECIMAL(18,2) NOT NULL, -- locked - actual
    has_force_majeure      BOOLEAN       NOT NULL DEFAULT FALSE,
    resolved_at            DATETIME(3)   NOT NULL,
    PRIMARY KEY (fact_id),
    UNIQUE KEY uq_fact_milestone (milestone_id),
    INDEX idx_fact_milestone_time (resolved_at),
    INDEX idx_fact_milestone_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS pending_force_majeure_flag (
    milestone_id CHAR(36)    NOT NULL,
    flagged_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (milestone_id)
);

-- One row per milestone, only ever locked (never read for data): lets the settled / cancelled and
-- force-majeure projections of the same milestone run one after the other (see lockMilestone).
CREATE TABLE IF NOT EXISTS milestone_projection_lock (
    milestone_id CHAR(36) NOT NULL,
    PRIMARY KEY (milestone_id)
);
