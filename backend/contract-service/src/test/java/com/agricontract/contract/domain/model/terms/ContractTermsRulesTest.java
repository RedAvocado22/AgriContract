package com.agricontract.contract.domain.model.terms;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractTermsRulesTest {

    @Test
    void validCoffeeTermsDeriveTotalFromPriceTimesScheduledQuantity() {
        ContractTermsSnapshot snapshot = snapshot();
        // 95,000.00 x (1,000 + 1,500.5) kg
        assertThat(snapshot.totalContractValue().wire()).isEqualTo("237547500.00");
        assertThat(snapshot.totalQuantity()).isEqualByComparingTo("2500.5");
    }

    @Test
    void totalContractValue_isRoundedToTheWholeDong_halfUp() {
        // D65: 95,000.01 × 2,500.5 kg = 237,547,525.005 → 237,547,525; deposits are taken from that rounded total.
        ContractTermsSnapshot snapshot = ContractTermsSnapshot.assemble(withPrice("95000.01"), goods(), template(), RULES);

        assertThat(snapshot.totalContractValue().wire()).isEqualTo("237547525.00");
        assertThat(snapshot.buyerDepositAmount().wire()).isEqualTo("11877376.00");
    }

    @Test
    void fundingLead_defaultsToSeven_whenLeftOut_andIsStoredInTheSnapshot() {
        ContractTermsSnapshot snapshot = ContractTermsSnapshot.assemble(withLead(null), goods(), template(), RULES);

        assertThat(snapshot.negotiated().fundingLeadBusinessDays()).isEqualTo(7);
        assertThat(snapshot.fundingLeadBusinessDays()).isEqualTo(7);
    }

    @Test
    void fundingLead_mustStayBetweenThreeAndSixty() {
        RULES.validateNegotiated(withLead(3));
        RULES.validateNegotiated(withLead(60));
        assertRejected(() -> RULES.validateNegotiated(withLead(2)), "fundingLeadBusinessDays");
        assertRejected(() -> RULES.validateNegotiated(withLead(61)), "fundingLeadBusinessDays");
    }

    @Test
    void nonCoffeeGoodsAreRejected() {
        CoffeeGoodsTerms rice = new CoffeeGoodsTerms("RICE", goods().listingId(), goods().productId(),
                goods().categoryId(), "Gạo", "ST25", "KG", 2026, null, goods().originSnapshot(), null);
        assertRejected(() -> ContractTermsSnapshot.assemble(input(), rice, template(), RULES), "only COFFEE");
    }

    @Test
    void nonCoffeeQualityTermsAreRejected() {
        ContractTermsInput t = input();
        CoffeeQualityTerms rubber = new CoffeeQualityTerms("RUBBER", t.qualityTerms().committedSpec(),
                t.qualityTerms().deviationPolicy());
        assertRejected(() -> RULES.validateNegotiated(with(t, rubber)), "only COFFEE");
    }

    @Test
    void coffeeWithoutPlotIsRejected() {
        assertRejected(() -> RULES.validateGoods(goods(List.of())), "at least one plot");
    }

    @Test
    void penaltyAboveTheCommercialLawCapIsRejected() {
        ContractTermsInput t = input();
        ContractTermsInput tooHigh = new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(),
                t.qualityTerms(), t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(),
                t.toleranceRate(), t.shortfallPenaltyThreshold(), new BigDecimal("0.0801"), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(), t.fundingLeadBusinessDays());
        assertRejected(() -> RULES.validateNegotiated(tooHigh), "buyerPenaltyRate (legal cap)");
    }

    @Test
    void clientCannotRaiseTheLegalCap() {
        LegalProfile raised = new LegalProfile(GoverningLaw.VN_COMMERCIAL_LAW, "COFFEE_SALE", new BigDecimal("0.30"),
                DamagesPolicy.COMMERCIAL_CUMULATIVE_IF_PROVEN);
        assertRejected(() -> RULES.validateNegotiated(with(input(), raised)), "must be 0.08");
    }

    @Test
    void civilLawHasNoPercentageCapButOnlyItsOwnDamagesPolicies() {
        LegalProfile civil = new LegalProfile(GoverningLaw.VN_CIVIL_LAW, "COFFEE_SALE", BigDecimal.ONE,
                DamagesPolicy.CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE);
        RULES.validateNegotiated(with(input(), civil));

        LegalProfile wrongPolicy = new LegalProfile(GoverningLaw.VN_CIVIL_LAW, "COFFEE_SALE", BigDecimal.ONE,
                DamagesPolicy.COMMERCIAL_CUMULATIVE_IF_PROVEN);
        assertRejected(() -> RULES.validateNegotiated(with(input(), wrongPolicy)), "not allowed under VN_CIVIL_LAW");
    }

    @Test
    void agreedPriceMustLieInsideThePriceBand() {
        ContractTermsInput t = input();
        PriceBand band = new PriceBand(VndAmount.parse("96000.00"), VndAmount.parse("100000.00"));
        ContractTermsInput outside = new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(),
                t.qualityTerms(), t.deliveryTerms(), t.inspectionTerms(), band, t.priceAdjustmentRule(),
                t.toleranceRate(), t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(), t.fundingLeadBusinessDays());
        assertRejected(() -> RULES.validateNegotiated(outside), "inside priceBand");
    }

    @Test
    void levelTwoInspectionNeedsAnInspectorOrganisation() {
        ContractTermsInput t = input();
        ContractTermsInput levelTwo = new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(),
                t.qualityTerms(), t.deliveryTerms(), inspection(InspectionTier.LEVEL_2), t.priceBand(),
                t.priceAdjustmentRule(), t.toleranceRate(), t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(),
                t.sellerPenaltyRate(), t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(),
                t.buyerDepositRate(), t.disputeFloorReleaseRate(), t.sellerDepositRate(), null, t.legalProfile(), t.fundingLeadBusinessDays());
        assertRejected(() -> RULES.validateNegotiated(levelTwo), "level2InspectorOrg is required");
    }

    @Test
    void milestoneIndexesMustRunWithoutGaps() {
        ContractTermsInput t = input();
        List<MilestoneTerm> gap = List.of(new MilestoneTerm(1, BigDecimal.TEN, LocalDate.of(2026, 12, 1), 0),
                new MilestoneTerm(3, BigDecimal.TEN, LocalDate.of(2026, 12, 2), 0));
        ContractTermsInput withGap = new ContractTermsInput(t.agreedPrice(), t.currency(), gap, t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(), t.fundingLeadBusinessDays());
        assertRejected(() -> RULES.validateNegotiated(withGap), "without gaps");
    }

    @Test
    void qualityDeviationMustEscalateFromToleranceToPenaltyToReject() {
        ContractTermsInput t = input();
        NumericDeviationPolicy inverted = new NumericDeviationPolicy(DeviationDirection.MAXIMUM,
                new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("0.02"));
        CoffeeQualityDeviationPolicy policy = t.qualityTerms().deviationPolicy();
        CoffeeQualityTerms bad = new CoffeeQualityTerms("COFFEE", t.qualityTerms().committedSpec(),
                new CoffeeQualityDeviationPolicy(policy.type(), inverted, policy.foreignMatterPercent(),
                        policy.blackBrokenBeansPercent()));
        assertRejected(() -> RULES.validateNegotiated(with(t, bad)), "tolerance <= penalty < reject");
    }

    @Test
    void qualityPercentagesAllowAtMostTwoDecimals() {
        ContractTermsInput t = input();
        CoffeeQualitySpec spec = t.qualityTerms().committedSpec();
        CoffeeQualityTerms threeDecimals = new CoffeeQualityTerms("COFFEE",
                new CoffeeQualitySpec("COFFEE", spec.type(), new BigDecimal("12.505"), spec.foreignMatterPercent(),
                        spec.blackBrokenBeansPercent()),
                t.qualityTerms().deviationPolicy());
        assertRejected(() -> RULES.validateNegotiated(with(t, threeDecimals)), "at most 2 decimals");
    }

    @Test
    void newBuyerDepositMustStayInsideTheNegotiationBand() {
        ContractTermsInput t = input();
        ContractTermsInput lowDeposit = new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(),
                t.qualityTerms(), t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(),
                t.toleranceRate(), t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), new BigDecimal("0.02"),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(), t.fundingLeadBusinessDays());
        assertRejected(() -> RULES.validateNegotiated(lowDeposit), "buyerDepositRate");
    }

    @Test
    void templateFingerprintMustBeASha256() {
        ContractTemplateProfile typed = new ContractTemplateProfile("VN_COFFEE_B2B", "1.0", "not-a-hash", "1.0");
        assertRejected(() -> RULES.validateTemplate(typed), "contractTemplateHash");
    }

    private static ContractTermsInput with(ContractTermsInput t, CoffeeQualityTerms quality) {
        return new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(), quality,
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(), t.fundingLeadBusinessDays());
    }

    private static ContractTermsInput with(ContractTermsInput t, LegalProfile legal) {
        return new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(), t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), legal,
                t.fundingLeadBusinessDays());
    }

    private static void assertRejected(Runnable action, String message) {
        assertThatThrownBy(action::run).isInstanceOf(InvalidContractTermsException.class).hasMessageContaining(message);
    }

    private static ContractTermsInput withLead(Integer days) {
        ContractTermsInput t = input();
        return new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(), t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(), days);
    }

    private static ContractTermsInput withPrice(String price) {
        ContractTermsInput t = input();
        return new ContractTermsInput(VndAmount.parse(price), t.currency(), t.milestoneSchedule(), t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile(),
                t.fundingLeadBusinessDays());
    }
}
