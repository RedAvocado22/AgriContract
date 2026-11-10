package com.agricontract.contract.application.dto;

import java.util.UUID;

/** POST /api/v1/contracts/{contractId}/withdraw (#233): the caller and the Idempotency-Key; the body is empty. */
public record WithdrawOfferCommand(String contractId, String actorId, UUID idempotencyKey) {
}
