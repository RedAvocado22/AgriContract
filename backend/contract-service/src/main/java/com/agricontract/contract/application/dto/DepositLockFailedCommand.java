package com.agricontract.contract.application.dto;

/** escrow.deposit_lock_failed, parsed (golden-flow-events.yaml escrowDepositLockFailed). */
public record DepositLockFailedCommand(String eventId, String correlationId, String contractId, String failedLeg,
                                       String buyerDepositState, String sellerDepositState, String reason) {
}
