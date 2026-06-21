package com.agricontract.escrow.application.dto;

import com.agricontract.escrow.domain.model.vo.EscrowStatus;

import java.math.BigDecimal;

public record EscrowAccountResponse(
        String escrowId,
        String contractId,
        String buyerUserId,
        String sellerUserId,
        BigDecimal totalAmount,
        BigDecimal sellerDeposit,
        String currency,
        EscrowStatus status
) {
}
