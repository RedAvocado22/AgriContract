package com.agricontract.contract.domain.model.terms;

public record CoffeeQualityTerms(
        String commodity,
        CoffeeQualitySpec committedSpec,
        CoffeeQualityDeviationPolicy deviationPolicy) {
}
