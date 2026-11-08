package com.agricontract.escrow.application.port;

import com.agricontract.escrow.domain.model.ActivationDeposits;

/**
 * Activation deposit outcomes to the outbox. Must run inside the caller's transaction, so the event exists exactly
 * when the leg state that ends the set commits.
 */
public interface DepositEventPublisher {

    /**
     * escrow.deposit_locked (golden-flow-events.yaml escrowDepositLocked). hasSellerLeg: false when the contract has
     * no seller deposit, so sellerDepositState is null.
     */
    void depositLocked(String contractId, boolean hasSellerLeg, String correlationId, String causationId);

    /**
     * escrow.deposit_lock_failed (escrowDepositLockFailed): nothing is left in progress and at least one leg's lock
     * failed with no retry left (#247).
     */
    void depositLockFailed(String contractId, ActivationDeposits deposits, String correlationId, String causationId);

    /**
     * escrow.activation_refund_completed (escrowActivationRefundCompleted, #247): every leg is settled on the cancel
     * path; returnedLegs lists the deposits given back (empty when none was locked).
     */
    void activationRefundCompleted(String contractId, ActivationDeposits deposits, String correlationId,
                                   String causationId);
}
