-- #247, D41/D42: returning activation deposits when the parties cancel a contract whose deposit lock failed.
-- New states: CANCEL_PENDING (a lock request is in flight: wait for its result), CANCELLED (never locked, nothing to
-- return), RETURN_REQUESTED / RETURN_FAILED (bank.refund_requested or bank.release_requested sent / refused, retried
-- per the same schedule), RETURNED (bank confirmed; return_ledger_entry_id is its entry).
-- return_request_event_id is the eventId = sourceEventId of the return command; a retry resends the same id.
ALTER TABLE deposit_leg
    ADD COLUMN return_request_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER last_failure_reason,
    ADD COLUMN return_ledger_entry_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER return_request_event_id,
    ADD UNIQUE KEY uk_deposit_leg_return_event (return_request_event_id),
    DROP CHECK chk_deposit_leg_status,
    DROP CHECK chk_deposit_leg_locked;

ALTER TABLE deposit_leg
    ADD CONSTRAINT chk_deposit_leg_status CHECK (status IN (
        'REQUESTED', 'LOCKED', 'FAILED', 'CANCEL_PENDING', 'CANCELLED',
        'RETURN_REQUESTED', 'RETURN_FAILED', 'RETURNED')),
    -- The money was locked exactly when the leg is LOCKED or on its way back.
    ADD CONSTRAINT chk_deposit_leg_locked CHECK (
        (status IN ('LOCKED', 'RETURN_REQUESTED', 'RETURN_FAILED', 'RETURNED')) = (ledger_entry_id IS NOT NULL)),
    ADD CONSTRAINT chk_deposit_leg_return CHECK (
        (status IN ('RETURN_REQUESTED', 'RETURN_FAILED', 'RETURNED')) = (return_request_event_id IS NOT NULL)),
    ADD CONSTRAINT chk_deposit_leg_returned CHECK ((status = 'RETURNED') = (return_ledger_entry_id IS NOT NULL));
