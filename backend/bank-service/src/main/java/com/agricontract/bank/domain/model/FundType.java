package com.agricontract.bank.domain.model;

// Which locked pool an entry moves money in/out of. Mirrors chk_ledger_fund_type.
public enum FundType {
    BUYER_DEPOSIT,
    SELLER_DEPOSIT,
    MILESTONE_PAYMENT
}
