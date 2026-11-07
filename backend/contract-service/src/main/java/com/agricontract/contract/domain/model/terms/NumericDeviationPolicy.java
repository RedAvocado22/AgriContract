package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;

public record NumericDeviationPolicy(
        DeviationDirection direction,
        BigDecimal tolerance,
        BigDecimal penalty,
        BigDecimal reject,
        BigDecimal discountRate) {
}
