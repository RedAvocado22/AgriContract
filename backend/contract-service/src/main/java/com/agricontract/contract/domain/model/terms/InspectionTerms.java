package com.agricontract.contract.domain.model.terms;

public record InspectionTerms(
        InspectionRequirement requirement,
        InspectionTier requiredTier,
        Integer reportDueBusinessDays,
        InspectionCostResponsibility inspectionCostResponsibility) {
}
