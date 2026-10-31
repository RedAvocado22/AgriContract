package com.agricontract.inspection.domain.model;

import static com.agricontract.inspection.domain.model.HashMaterialValidation.percent;
import static com.agricontract.inspection.domain.model.HashMaterialValidation.require;

import java.math.BigDecimal;

public record CoffeeActualMetrics(
        String commodity,
        CoffeeType type,
        BigDecimal moisturePercent,
        BigDecimal foreignMatterPercent,
        BigDecimal blackBrokenBeansPercent) {
    public CoffeeActualMetrics {
        require("COFFEE".equals(commodity), "Only COFFEE is supported");
        require(type != null, "Coffee type required");
        percent(moisturePercent);
        percent(foreignMatterPercent);
        percent(blackBrokenBeansPercent);
    }
}
