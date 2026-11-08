-- #102: bank results on deposit legs (D39). Additive: the V9 rows are REQUESTED with no result yet.
--   LOCKED  bank.lock_completed arrived; ledger_entry_id is the bank ledger row. Never goes back (bank ledger is
--           the truth): a late bank.lock_failed for a LOCKED leg is ignored.
--   FAILED  the last bank.lock_failed; next_attempt_at is when the same request_event_id is resent, NULL once the
--           retries are used up (#247 then publishes escrow.deposit_lock_failed).
-- failed_attempts counts bank.lock_failed results for this leg; the backoff schedule is read from it.
ALTER TABLE deposit_leg
    ADD COLUMN ledger_entry_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER request_event_id,
    ADD COLUMN failed_attempts     INT          NOT NULL DEFAULT 0 AFTER ledger_entry_id,
    ADD COLUMN next_attempt_at     DATETIME(6)  NULL AFTER failed_attempts,
    ADD COLUMN last_failure_reason VARCHAR(500) NULL AFTER next_attempt_at,
    ADD CONSTRAINT chk_deposit_leg_status CHECK (status IN ('REQUESTED', 'LOCKED', 'FAILED')),
    ADD CONSTRAINT chk_deposit_leg_locked CHECK ((status = 'LOCKED') = (ledger_entry_id IS NOT NULL)),
    ADD CONSTRAINT chk_deposit_leg_attempts CHECK (failed_attempts >= 0),
    ADD INDEX idx_deposit_leg_retry (status, next_attempt_at);
