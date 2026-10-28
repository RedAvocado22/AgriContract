package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.exception.InvalidDepositLegException;

import java.math.BigDecimal;
import java.math.RoundingMode;

// VND amount, DECIMAL(18,2), strictly positive (same rules as bank-service Money). Escrow takes the amount
// contract-service computed and never re-rounds it: an amount needing rounding is rejected instead.
public record Money(BigDecimal amount) {

    private static final int SCALE = 2;
    private static final int MAX_PRECISION = 18;

    public Money {
        if (amount == null) {
            throw new InvalidDepositLegException("amount is required");
        }
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidDepositLegException("amount has more than 2 decimal places: " + amount.toPlainString());
        }
        if (amount.signum() <= 0) {
            throw new InvalidDepositLegException("amount must be strictly positive: " + amount.toPlainString());
        }
        if (amount.precision() > MAX_PRECISION) {
            throw new InvalidDepositLegException("amount exceeds DECIMAL(18,2): " + amount.toPlainString());
        }
    }
}
