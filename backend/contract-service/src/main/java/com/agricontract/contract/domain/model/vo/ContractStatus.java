package com.agricontract.contract.domain.model.vo;

public enum ContractStatus {
    OFFERED, NEGOTIATING, SIGNED, ACTIVE, SETTLED,
    /** D42: the parties cancelled a SIGNED contract whose deposit lock failed; escrow returns the locked deposits. */
    ACTIVATION_REFUND_PENDING,
    /** Terminal: every deposit returned to its owner, no penalty, no reputation effect. */
    ACTIVATION_FAILED,
    /** Terminal (#233, WITHDRAW_OFFER): a party ended the offer before the second signature. Nothing was locked. */
    WITHDRAWN
}
