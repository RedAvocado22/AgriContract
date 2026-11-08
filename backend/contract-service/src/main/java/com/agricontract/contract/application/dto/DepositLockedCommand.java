package com.agricontract.contract.application.dto;

/** escrow.deposit_locked, parsed by the consumer. sellerDepositState is null when there is no seller deposit. */
public record DepositLockedCommand(
        String eventId,
        String correlationId,
        String contractId,
        String buyerDepositState,
        String sellerDepositState) {
}
