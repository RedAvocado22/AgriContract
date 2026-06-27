package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Money amount must be positive");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("Money currency must not be blank");
    }
}
