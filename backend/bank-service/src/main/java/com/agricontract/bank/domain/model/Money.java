package com.agricontract.bank.domain.model;

import com.agricontract.bank.domain.exception.InvalidLedgerEntryException;

import java.math.BigDecimal;
import java.math.RoundingMode;

// VND amount stored as DECIMAL(18,2). The ledger has no currency column. Always held at scale 2, so record equality
// already treats 100.5 and 100.50 as the same amount.
// Rounding happens only in contract-service (bank design §2b.2); the bank never re-rounds, so an amount that would
// need rounding is rejected instead of silently drifting from the remedy legs and the audit record.
public record Money(BigDecimal amount) {

    private static final int SCALE = 2;
    private static final int MAX_PRECISION = 18;

    public Money {
        if (amount == null) {
            throw new InvalidLedgerEntryException("amount is required");
        }
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidLedgerEntryException("amount has more than 2 decimal places: " + amount.toPlainString());
        }
        if (amount.signum() <= 0) {
            throw new InvalidLedgerEntryException("amount must be strictly positive: " + amount.toPlainString());
        }
        if (amount.precision() > MAX_PRECISION) {
            throw new InvalidLedgerEntryException("amount exceeds DECIMAL(18,2): " + amount.toPlainString());
        }
    }
}
