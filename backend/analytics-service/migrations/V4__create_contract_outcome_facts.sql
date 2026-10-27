-- analytics-service — additive migration (creates new tables only), issue #148.
-- Contract-level facts from analytics-service-phase2-design.md §2.2 and §3.3 (matrix 26g).
-- Differences from the design sketch, on purpose:
--  * timestamps are DATETIME(3) in UTC (the pool runs in UTC) instead of TIMESTAMP, like V3;
--  * requested_by is VARCHAR(10) and final_breaching_role is a separate nullable column: who asked to
--    terminate is never who breached (milestone-escrow §6.0);
--  * remedy_decision_id is kept: contract.terminated carries it so analytics needs no remedy.finalized
--    consumer (design §3.3), and it links a breach termination to its remedy decision;
--  * both tables have UNIQUE (contract_id): a contract terminates or settles once, so a replayed outcome
--    can never be counted twice, even under a new event id.

CREATE TABLE IF NOT EXISTS fact_contract_termination (
    fact_id              CHAR(36)    NOT NULL,
    contract_id          CHAR(36)    NOT NULL,
    termination_type     VARCHAR(35) NOT NULL, -- WITHDRAW_OFFER / MUTUAL_TERMINATION / MUTUAL_REPLACEMENT /
                                               -- TERMINATION_FOR_BREACH / TERMINATION_FOR_FORCE_MAJEURE / ACTIVATION_FAILURE
    requested_by         VARCHAR(10) NOT NULL, -- BUYER / SELLER / SYSTEM: who pressed the button
    final_breaching_role VARCHAR(10) NULL,     -- BUYER / SELLER / NULL: who is at fault after attribution
    breach_reason_code   VARCHAR(30) NULL,     -- taxonomy milestone-escrow §6.4.1
    remedy_decision_id   CHAR(36)    NULL,
    terminated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (fact_id),
    UNIQUE KEY uq_fact_termination_contract (contract_id),
    INDEX idx_fact_termination_time (terminated_at)
);

CREATE TABLE IF NOT EXISTS fact_contract_settlement (
    fact_id     CHAR(36)    NOT NULL,
    contract_id CHAR(36)    NOT NULL,
    settled_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (fact_id),
    UNIQUE KEY uq_fact_settlement_contract (contract_id),
    INDEX idx_fact_contract_settlement_time (settled_at)
);
