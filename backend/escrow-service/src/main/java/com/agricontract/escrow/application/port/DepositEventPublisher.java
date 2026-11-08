package com.agricontract.escrow.application.port;

/**
 * escrow.deposit_locked (golden-flow-events.yaml escrowDepositLocked) to the outbox. Must run inside the caller's
 * transaction, so the event exists exactly when the last leg's LOCKED state commits.
 */
public interface DepositEventPublisher {

    /** hasSellerLeg: false when the contract has no seller deposit, so sellerDepositState is null. */
    void depositLocked(String contractId, boolean hasSellerLeg, String correlationId, String causationId);
}
