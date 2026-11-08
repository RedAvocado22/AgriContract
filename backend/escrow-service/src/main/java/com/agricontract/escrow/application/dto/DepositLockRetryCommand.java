package com.agricontract.escrow.application.dto;

/** contract.deposit_lock_retry_requested, parsed: OPERATOR asked to lock the failed deposit legs again (D42). */
public record DepositLockRetryCommand(String eventId, String correlationId, String contractId, String operatorUserId) {
}
