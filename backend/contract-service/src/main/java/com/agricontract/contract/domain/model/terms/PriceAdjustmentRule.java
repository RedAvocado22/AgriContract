package com.agricontract.contract.domain.model.terms;

public record PriceAdjustmentRule(
        AcceptanceMode acceptanceMode,
        AdjustmentCutoff cutoff,
        Integer maximumAcceptedAdjustmentsPerMilestone) {
}
