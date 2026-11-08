-- #234, D42: a SIGNED contract whose activation deposits could not be locked (escrow.deposit_lock_failed).
-- deposit_lock_failed_at is the FIRST failure and never moves: one_sided_cancel_allowed_from is computed from it
-- (+ activationCancelOneSidedWindowBusinessDays on the shared Vietnam calendar) and an OPERATOR retry does not move
-- it either. The last_* columns describe the latest report, for OPERATOR and the notification.
CREATE TABLE activation_recovery (
    contract_id                    VARCHAR(255) NOT NULL,
    deposit_lock_failed_at         TIMESTAMP(6) NOT NULL,
    one_sided_cancel_allowed_from  TIMESTAMP(6) NOT NULL,
    last_failed_leg                VARCHAR(8)   NOT NULL,
    last_failure_reason            VARCHAR(500) NULL,
    last_failed_at                 TIMESTAMP(6) NOT NULL,
    failure_reports                INT          NOT NULL,
    PRIMARY KEY (contract_id),
    CONSTRAINT fk_activation_recovery_contract FOREIGN KEY (contract_id) REFERENCES contracts (contract_id),
    CONSTRAINT chk_activation_recovery_leg CHECK (last_failed_leg IN ('BUYER', 'SELLER', 'BOTH')),
    CONSTRAINT chk_activation_recovery_window CHECK (one_sided_cancel_allowed_from > deposit_lock_failed_at),
    CONSTRAINT chk_activation_recovery_reports CHECK (failure_reports >= 1)
);
