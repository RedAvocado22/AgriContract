package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.util.List;

/** The part of the terms the parties negotiate (create/PATCH). goodsTerms and totalContractValue are server-derived. */
public record ContractTermsInput(
        VndAmount agreedPrice,
        String currency,
        List<MilestoneTerm> milestoneSchedule,
        CoffeeQualityTerms qualityTerms,
        DeliveryTerms deliveryTerms,
        InspectionTerms inspectionTerms,
        PriceBand priceBand,
        PriceAdjustmentRule priceAdjustmentRule,
        BigDecimal toleranceRate,
        BigDecimal shortfallPenaltyThreshold,
        BigDecimal buyerPenaltyRate,
        BigDecimal sellerPenaltyRate,
        Integer forceMajeureReportWindowDays,
        Integer buyerConfirmWindowDays,
        BigDecimal buyerDepositRate,
        BigDecimal disputeFloorReleaseRate,
        BigDecimal sellerDepositRate,
        String level2InspectorOrg,
        LegalProfile legalProfile) {

    public ContractTermsInput {
        milestoneSchedule = milestoneSchedule == null ? null : List.copyOf(milestoneSchedule);
    }
}
