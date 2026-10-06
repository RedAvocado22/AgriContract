package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;
import java.util.Objects;

public record Quantity(BigDecimal value, String unit) {
    public Quantity {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Quantity value must be positive");
        if (unit == null || unit.isBlank())
            throw new IllegalArgumentException("Quantity unit must not be blank");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity other)) return false;
        return value.compareTo(other.value) == 0 && unit.equals(other.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros(), unit);
    }
}
