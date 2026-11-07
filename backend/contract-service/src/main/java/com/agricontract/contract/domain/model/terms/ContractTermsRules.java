package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for new create/PATCH terms (golden-flow-api ContractTermsBase/Input, milestone-escrow design §2.1–2.1c).
 * Already signed snapshots are never re-validated: a legacy deposit rate outside today's band stays readable.
 */
public class ContractTermsRules {
    public static final String COFFEE = "COFFEE";
    public static final String VND = "VND";
    public static final String KG = "KG";
    public static final String TEMPLATE_CODE = "VN_COFFEE_B2B";

    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999999999.99");
    private static final BigDecimal MAX_MILESTONE_KG = new BigDecimal("1000000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_MILESTONES = 100;

    private final BigDecimal level2SafetyBufferRate;

    /** level2SafetyBufferRate is configuration (design §8, 15%); it bounds disputeFloorReleaseRate from above. */
    public ContractTermsRules(BigDecimal level2SafetyBufferRate) {
        this.level2SafetyBufferRate = required(level2SafetyBufferRate, "level2SafetyBufferRate");
        within(level2SafetyBufferRate, BigDecimal.ZERO, new BigDecimal("0.50"), "level2SafetyBufferRate");
    }

    public void validateNegotiated(ContractTermsInput t) {
        required(t, "terms");
        VndAmount price = required(t.agreedPrice(), "agreedPrice");
        if (price.value().signum() <= 0 || price.value().compareTo(MAX_UNIT_PRICE) > 0) {
            fail("agreedPrice must be above 0 and at most " + MAX_UNIT_PRICE.toPlainString());
        }
        if (!VND.equals(t.currency())) {
            fail("currency must be VND");
        }
        validateSchedule(t.milestoneSchedule());
        validateQuality(required(t.qualityTerms(), "qualityTerms"));
        validateDelivery(required(t.deliveryTerms(), "deliveryTerms"));
        validateInspection(required(t.inspectionTerms(), "inspectionTerms"), t.level2InspectorOrg());
        validatePriceBand(required(t.priceBand(), "priceBand"), price);
        validateAdjustmentRule(required(t.priceAdjustmentRule(), "priceAdjustmentRule"));

        within(t.toleranceRate(), BigDecimal.ZERO, new BigDecimal("0.10"), "toleranceRate");
        within(t.shortfallPenaltyThreshold(), new BigDecimal("0.03"), new BigDecimal("0.15"), "shortfallPenaltyThreshold");

        BigDecimal cap = validateLegalProfile(required(t.legalProfile(), "legalProfile"));
        within(t.buyerPenaltyRate(), BigDecimal.ZERO, cap, "buyerPenaltyRate (legal cap)");
        within(t.sellerPenaltyRate(), BigDecimal.ZERO, cap, "sellerPenaltyRate (legal cap)");

        if (required(t.forceMajeureReportWindowDays(), "forceMajeureReportWindowDays") < 1) {
            fail("forceMajeureReportWindowDays must be at least 1");
        }
        int confirmDays = required(t.buyerConfirmWindowDays(), "buyerConfirmWindowDays");
        if (confirmDays < 2 || confirmDays > 10) {
            fail("buyerConfirmWindowDays must be between 2 and 10");
        }
        within(t.buyerDepositRate(), new BigDecimal("0.03"), new BigDecimal("0.10"), "buyerDepositRate");
        within(t.sellerDepositRate(), BigDecimal.ZERO, BigDecimal.ONE, "sellerDepositRate");
        within(t.disputeFloorReleaseRate(), new BigDecimal("0.50"), BigDecimal.ONE.subtract(level2SafetyBufferRate),
                "disputeFloorReleaseRate");
    }

    /** Phase 2 accepts only coffee, and coffee must name at least one plot (matrix 22). */
    public void validateGoods(CoffeeGoodsTerms g) {
        required(g, "goodsTerms");
        if (!COFFEE.equals(g.commodity())) {
            fail("Phase 2 accepts only COFFEE goods, got " + g.commodity());
        }
        required(g.listingId(), "goodsTerms.listingId");
        required(g.productId(), "goodsTerms.productId");
        required(g.categoryId(), "goodsTerms.categoryId");
        text(g.categoryName(), "goodsTerms.categoryName");
        text(g.productName(), "goodsTerms.productName");
        if (!KG.equals(g.unitOfMeasure())) {
            fail("goodsTerms.unitOfMeasure must be KG");
        }
        PlotBackedOriginSnapshot origin = required(g.originSnapshot(), "goodsTerms.originSnapshot");
        text(origin.regionText(), "goodsTerms.originSnapshot.regionText");
        if (origin.plotRefs() == null || origin.plotRefs().isEmpty()) {
            fail("Coffee goods must reference at least one plot");
        }
        for (PlotRefSnapshot plot : origin.plotRefs()) {
            required(plot, "plotRef");
            required(plot.registryEntryId(), "plotRef.registryEntryId");
            text(plot.householdLabel(), "plotRef.householdLabel");
            required(plot.geometryType(), "plotRef.geometryType");
            sha256(plot.geometryHash(), "plotRef.geometryHash");
            required(plot.verificationLevel(), "plotRef.verificationLevel");
        }
        if (g.traceabilitySnapshot() != null) {
            required(g.traceabilitySnapshot().evidencePackageId(), "traceabilitySnapshot.evidencePackageId");
            sha256(g.traceabilitySnapshot().packageHash(), "traceabilitySnapshot.packageHash");
        }
    }

    public void validateTemplate(ContractTemplateProfile p) {
        required(p, "template");
        if (!TEMPLATE_CODE.equals(p.contractTemplateCode())) {
            fail("contractTemplateCode must be " + TEMPLATE_CODE);
        }
        text(p.contractTemplateVersion(), "contractTemplateVersion");
        sha256(p.contractTemplateHash(), "contractTemplateHash");
        text(p.coffeeQualityProfileVersion(), "coffeeQualityProfileVersion");
    }

    public void validateTotal(VndAmount total) {
        if (total.value().compareTo(MAX_TOTAL) > 0) {
            fail("totalContractValue exceeds " + MAX_TOTAL.toPlainString());
        }
    }

    private void validateSchedule(List<MilestoneTerm> schedule) {
        if (schedule == null || schedule.isEmpty() || schedule.size() > MAX_MILESTONES) {
            fail("milestoneSchedule must have 1 to " + MAX_MILESTONES + " milestones");
        }
        Set<Integer> indexes = new HashSet<>();
        for (MilestoneTerm m : schedule) {
            required(m, "milestone");
            int index = required(m.milestoneIndex(), "milestoneIndex");
            if (index < 1 || !indexes.add(index)) {
                fail("milestoneIndex must be unique and start at 1, got " + index);
            }
            BigDecimal qty = required(m.committedQuantity(), "committedQuantity");
            if (qty.signum() <= 0 || qty.compareTo(MAX_MILESTONE_KG) > 0) {
                fail("committedQuantity must be above 0 and at most 1,000,000 kg");
            }
            required(m.expectedDeliveryDate(), "expectedDeliveryDate");
            if (required(m.graceDays(), "graceDays") < 0) {
                fail("graceDays must not be negative");
            }
        }
        if (indexes.size() != schedule.size() || !indexes.contains(schedule.size())) {
            fail("milestoneIndex must run 1.." + schedule.size() + " without gaps");
        }
    }

    private void validateQuality(CoffeeQualityTerms q) {
        if (!COFFEE.equals(q.commodity())) {
            fail("Phase 2 accepts only COFFEE quality terms, got " + q.commodity());
        }
        CoffeeQualitySpec spec = required(q.committedSpec(), "committedSpec");
        if (!COFFEE.equals(spec.commodity())) {
            fail("committedSpec.commodity must be COFFEE");
        }
        required(spec.type(), "committedSpec.type");
        percent(spec.moisturePercent(), "moisturePercent");
        percent(spec.foreignMatterPercent(), "foreignMatterPercent");
        percent(spec.blackBrokenBeansPercent(), "blackBrokenBeansPercent");

        CoffeeQualityDeviationPolicy policy = required(q.deviationPolicy(), "deviationPolicy");
        ExactMatchRejectPolicy typePolicy = required(policy.type(), "deviationPolicy.type");
        if (!Boolean.TRUE.equals(typePolicy.rejectOnMismatch())) {
            fail("deviationPolicy.type.rejectOnMismatch must be true");
        }
        deviation(policy.moisturePercent(), "moisturePercent");
        deviation(policy.foreignMatterPercent(), "foreignMatterPercent");
        deviation(policy.blackBrokenBeansPercent(), "blackBrokenBeansPercent");
    }

    private static void validateDelivery(DeliveryTerms d) {
        required(d.deliveryResponsibility(), "deliveryResponsibility");
        required(d.riskTransferPoint(), "riskTransferPoint");
        required(d.titleTransferPoint(), "titleTransferPoint");
        required(d.transportCostBearer(), "transportCostBearer");
        required(d.unloadingCostBearer(), "unloadingCostBearer");
    }

    private static void validateInspection(InspectionTerms i, String level2InspectorOrg) {
        required(i.requirement(), "inspectionTerms.requirement");
        InspectionTier tier = required(i.requiredTier(), "inspectionTerms.requiredTier");
        int days = required(i.reportDueBusinessDays(), "inspectionTerms.reportDueBusinessDays");
        if (days < 1 || days > 30) {
            fail("inspectionTerms.reportDueBusinessDays must be between 1 and 30");
        }
        required(i.inspectionCostResponsibility(), "inspectionTerms.inspectionCostResponsibility");
        if (tier == InspectionTier.LEVEL_2 && (level2InspectorOrg == null || level2InspectorOrg.isBlank())) {
            fail("level2InspectorOrg is required when requiredTier is LEVEL_2");
        }
    }

    private static void validatePriceBand(PriceBand band, VndAmount price) {
        VndAmount min = required(band.minimumUnitPrice(), "priceBand.minimumUnitPrice");
        VndAmount max = required(band.maximumUnitPrice(), "priceBand.maximumUnitPrice");
        if (min.compareTo(price) > 0 || price.compareTo(max) > 0) {
            fail("agreedPrice must lie inside priceBand");
        }
        if (max.value().compareTo(MAX_UNIT_PRICE) > 0) {
            fail("priceBand.maximumUnitPrice exceeds " + MAX_UNIT_PRICE.toPlainString());
        }
    }

    private static void validateAdjustmentRule(PriceAdjustmentRule r) {
        required(r.acceptanceMode(), "priceAdjustmentRule.acceptanceMode");
        required(r.cutoff(), "priceAdjustmentRule.cutoff");
        if (!Objects.equals(r.maximumAcceptedAdjustmentsPerMilestone(), 1)) {
            fail("priceAdjustmentRule.maximumAcceptedAdjustmentsPerMilestone must be 1");
        }
    }

    /** The cap is the law's, not the client's: a different submitted cap is rejected, never silently corrected. */
    private static BigDecimal validateLegalProfile(LegalProfile p) {
        GoverningLaw law = required(p.governingLaw(), "legalProfile.governingLaw");
        text(p.contractType(), "legalProfile.contractType");
        BigDecimal submitted = required(p.maxContractualPenaltyRate(), "legalProfile.maxContractualPenaltyRate");
        if (submitted.compareTo(law.maxContractualPenaltyRate()) != 0) {
            fail("legalProfile.maxContractualPenaltyRate must be " + law.maxContractualPenaltyRate().toPlainString()
                    + " under " + law);
        }
        DamagesPolicy policy = required(p.damagesPolicy(), "legalProfile.damagesPolicy");
        if (!law.allows(policy)) {
            fail("legalProfile.damagesPolicy " + policy + " is not allowed under " + law);
        }
        return law.maxContractualPenaltyRate();
    }

    private static void deviation(NumericDeviationPolicy p, String field) {
        required(p, "deviationPolicy." + field);
        required(p.direction(), "deviationPolicy." + field + ".direction");
        BigDecimal tolerance = nonNegative(p.tolerance(), field + ".tolerance");
        BigDecimal penalty = nonNegative(p.penalty(), field + ".penalty");
        BigDecimal reject = nonNegative(p.reject(), field + ".reject");
        if (tolerance.compareTo(penalty) > 0 || penalty.compareTo(reject) >= 0) {
            fail("deviationPolicy." + field + " needs tolerance <= penalty < reject");
        }
        within(p.discountRate(), BigDecimal.ZERO, BigDecimal.ONE, "deviationPolicy." + field + ".discountRate");
    }

    private static void percent(BigDecimal value, String field) {
        within(value, BigDecimal.ZERO, HUNDRED, "committedSpec." + field);
        if (value.stripTrailingZeros().scale() > 2) {
            fail("committedSpec." + field + " allows at most 2 decimals");
        }
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (required(value, field).signum() < 0) {
            fail(field + " must not be negative");
        }
        return value;
    }

    private static void within(BigDecimal value, BigDecimal min, BigDecimal max, String field) {
        required(value, field);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            fail(field + " must be between " + min.toPlainString() + " and " + max.toPlainString()
                    + ", got " + value.toPlainString());
        }
    }

    private static void sha256(String value, String field) {
        if (value == null || !SHA256_HEX.matcher(value).matches()) {
            fail(field + " must be a lowercase SHA-256 hex string");
        }
    }

    private static void text(String value, String field) {
        if (value == null || value.isBlank()) {
            fail(field + " is required");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            fail(field + " is required");
        }
        return value;
    }

    private static void fail(String message) {
        throw new InvalidContractTermsException(message);
    }
}
