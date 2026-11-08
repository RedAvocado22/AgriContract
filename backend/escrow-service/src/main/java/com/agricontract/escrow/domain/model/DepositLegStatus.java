package com.agricontract.escrow.domain.model;

/**
 * Where a deposit leg stands with the bank (#102, D39; return on cancel #247, D41/D42).
 * <ul>
 *   <li>REQUESTED: bank.lock_requested sent (first time or a retry), no result yet.</li>
 *   <li>LOCKED: bank.lock_completed; the bank ledger holds the money.</li>
 *   <li>FAILED: the last lock result was bank.lock_failed; a retry is due at nextAttemptAt, or none is left.</li>
 *   <li>CANCEL_PENDING: the parties cancelled while a lock request was in flight; its result decides: locked → the
 *       money is returned, failed → CANCELLED.</li>
 *   <li>CANCELLED: cancelled before any money was locked; nothing to return.</li>
 *   <li>RETURN_REQUESTED: the locked money is being returned (bank.refund_requested for the buyer,
 *       bank.release_requested for the seller), no result yet.</li>
 *   <li>RETURN_FAILED: the bank refused the return; a resend is due at nextAttemptAt, or none is left (alert).</li>
 *   <li>RETURNED: the bank confirmed the return; final.</li>
 * </ul>
 */
public enum DepositLegStatus {
    REQUESTED,
    LOCKED,
    FAILED,
    CANCEL_PENDING,
    CANCELLED,
    RETURN_REQUESTED,
    RETURN_FAILED,
    RETURNED
}
