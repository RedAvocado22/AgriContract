package com.agricontract.escrow.domain.model.vo;

public enum TransactionType {
    LOCK,
    REFUND_TO_BUYER,
    REFUND_TO_SELLER,
    RELEASE,
    PENALIZE_BUYER,
    PENALIZE_SELLER,
    ARBITRATION
}
