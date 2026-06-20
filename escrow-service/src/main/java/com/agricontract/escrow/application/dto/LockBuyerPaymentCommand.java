package com.agricontract.escrow.application.dto;

import com.agricontract.escrow.domain.model.vo.Money;

import java.math.BigDecimal;

public record LockBuyerPaymentCommand(String contractId, String buyerId, String sellerId,
                                       String buyerEmail, String sellerEmail,
                                       BigDecimal sellerDepositRate, Money agreedPrice) {
}
