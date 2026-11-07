package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;

public record CoffeeQualitySpec(
        String commodity,
        CoffeeType type,
        BigDecimal moisturePercent,
        BigDecimal foreignMatterPercent,
        BigDecimal blackBrokenBeansPercent) {
}
