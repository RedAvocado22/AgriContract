-- Contract-level deposit legs locked at activation (milestone-escrow design §2.3, §3.1; #101).
-- One row per required deposit: BUYER always, SELLER only when sellerDepositAmount > 0.
-- Escrow keeps the leg's state, not a balance: amounts are the bank.lock_requested command values
-- taken from contract.signed; the money itself lives in bank-service ledger_entry.
CREATE TABLE deposit_leg
(
    leg_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    leg              VARCHAR(10)   NOT NULL,   -- BUYER | SELLER
    user_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount           DECIMAL(18,2) NOT NULL,
    status           VARCHAR(30)   NOT NULL,   -- REQUESTED on creation; #102 adds the result states
    request_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                               -- eventId = sourceEventId of bank.lock_requested; a retry resends
                                               -- this same id so bank dedups it (D27)
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,

    PRIMARY KEY (leg_id),
    UNIQUE KEY uk_deposit_leg_contract_leg (contract_id, leg),  -- a redelivered contract.signed adds no leg
    UNIQUE KEY uk_deposit_leg_request_event (request_event_id),
    CONSTRAINT chk_deposit_leg_leg CHECK (leg IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_deposit_leg_amount CHECK (amount > 0),
    CONSTRAINT chk_deposit_leg_time CHECK (updated_at >= created_at)
);
