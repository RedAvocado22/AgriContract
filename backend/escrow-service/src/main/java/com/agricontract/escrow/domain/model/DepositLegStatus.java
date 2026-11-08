package com.agricontract.escrow.domain.model;

/**
 * Where a deposit leg stands with the bank (#102, D39).
 * REQUESTED: bank.lock_requested sent (first time or a retry), no result yet.
 * LOCKED: bank.lock_completed; final, the bank ledger holds the money.
 * FAILED: the last result was bank.lock_failed; a retry is due at nextAttemptAt, or none is left.
 */
public enum DepositLegStatus {
    REQUESTED,
    LOCKED,
    FAILED
}
