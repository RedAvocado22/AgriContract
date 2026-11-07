package com.agricontract.contract.domain.model.terms;

public record CoffeeQualityDeviationPolicy(
        ExactMatchRejectPolicy type,
        NumericDeviationPolicy moisturePercent,
        NumericDeviationPolicy foreignMatterPercent,
        NumericDeviationPolicy blackBrokenBeansPercent) {
}
