package com.agricontract.escrow.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return this.amount.compareTo(other.amount) == 0 && this.currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    public Money add(Money other) {
        isValid(other);

        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        isValid(other);

        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal multiplier) {
        return new Money(amount.multiply(multiplier).setScale(0, RoundingMode.HALF_EVEN), currency);
    }

    private void isValid(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(String.format("Currency has to be equal to currency %s", other.currency));
        }
    }
}
