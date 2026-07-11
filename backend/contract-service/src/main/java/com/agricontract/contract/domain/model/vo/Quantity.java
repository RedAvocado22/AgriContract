package com.agricontract.contract.domain.model.vo;

import java.math.BigDecimal;
import java.util.Objects;

public record Quantity(BigDecimal value, String unit) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity other)) return false;
        return this.value.compareTo(other.value) == 0 && this.unit.equals(other.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros(), unit);
    }
}
