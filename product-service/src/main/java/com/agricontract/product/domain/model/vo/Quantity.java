package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;

public record Quantity(BigDecimal value, String unit) {
    public Quantity {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Quantity value must be positive");
        if (unit == null || unit.isBlank())
            throw new IllegalArgumentException("Quantity unit must not be blank");
    }
}
