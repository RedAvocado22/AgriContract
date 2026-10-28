package com.agricontract.escrow.application.dto;

import java.math.BigDecimal;

/**
 * What escrow needs from contract.signed (golden-flow-events.yaml contractSigned) to request the activation deposits.
 * eventId and correlationId come from the envelope: the lock commands carry correlationId through and use eventId
 * as their causationId. sellerDepositAmount is 0.00 when the contract has no seller deposit.
 */
public record ContractSignedCommand(
        String eventId,
        String correlationId,
        String contractId,
        String buyerId,
        String sellerId,
        BigDecimal buyerDepositAmount,
        BigDecimal sellerDepositAmount
) {}
