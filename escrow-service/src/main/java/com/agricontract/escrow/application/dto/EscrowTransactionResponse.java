package com.agricontract.escrow.application.dto;

import com.agricontract.escrow.domain.model.vo.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EscrowTransactionResponse(
        String transactionId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String note,
        LocalDateTime createdAt
) {
}
