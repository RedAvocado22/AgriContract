package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A valid coffee contract; tests change one field at a time. */
public final class CoffeeTermsFixtures {
    public static final ContractTermsRules RULES = new ContractTermsRules(new BigDecimal("0.15"));
    public static final String HASH_A = "a".repeat(64);
    public static final String HASH_B = "b".repeat(64);

    private CoffeeTermsFixtures() {
    }

    public static ContractTermsInput input() {
        return new ContractTermsInput(
                VndAmount.parse("95000.00"), "VND",
                List.of(new MilestoneTerm(1, new BigDecimal("1000"), LocalDate.of(2026, 12, 15), 2),
                        new MilestoneTerm(2, new BigDecimal("1500.5"), LocalDate.of(2027, 1, 20), 2)),
                quality(), delivery(), inspection(InspectionTier.LEVEL_1_5),
                new PriceBand(VndAmount.parse("90000.00"), VndAmount.parse("100000.00")),
                new PriceAdjustmentRule(AcceptanceMode.BOTH_ACCEPT, AdjustmentCutoff.BEFORE_FUNDING_REQUEST, 1),
                new BigDecimal("0.05"), new BigDecimal("0.05"), new BigDecimal("0.08"), new BigDecimal("0.06"),
                7, 3, new BigDecimal("0.05"), new BigDecimal("0.50"), new BigDecimal("0.03"), null,
                commercialLaw());
    }

    public static LegalProfile commercialLaw() {
        return new LegalProfile(GoverningLaw.VN_COMMERCIAL_LAW, "COFFEE_SALE", new BigDecimal("0.08"),
                DamagesPolicy.COMMERCIAL_CUMULATIVE_IF_PROVEN);
    }

    public static CoffeeQualityTerms quality() {
        NumericDeviationPolicy max = new NumericDeviationPolicy(DeviationDirection.MAXIMUM,
                new BigDecimal("0.5"), new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("0.02"));
        return new CoffeeQualityTerms("COFFEE",
                new CoffeeQualitySpec("COFFEE", CoffeeType.ROBUSTA, new BigDecimal("12.5"), new BigDecimal("0.5"),
                        new BigDecimal("5.0")),
                new CoffeeQualityDeviationPolicy(new ExactMatchRejectPolicy(true), max, max, max));
    }

    public static DeliveryTerms delivery() {
        return new DeliveryTerms(DeliveryResponsibility.SELLER_DELIVERS, RiskTransferPoint.BUYER_RECEIPT,
                TitleTransferPoint.FULL_PAYMENT, CostBearer.SELLER, CostBearer.BUYER);
    }

    public static InspectionTerms inspection(InspectionTier tier) {
        return new InspectionTerms(InspectionRequirement.NONE_UNLESS_DISPUTED, tier, 5,
                InspectionCostResponsibility.LOSER_PAYS);
    }

    public static CoffeeGoodsTerms goods() {
        return goods(List.of(plot()));
    }

    public static CoffeeGoodsTerms goods(List<PlotRefSnapshot> plots) {
        return new CoffeeGoodsTerms("COFFEE",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "Cà phê nhân", "Robusta Đắk Lắk loại 1", "KG", 2026, "2026/27",
                new PlotBackedOriginSnapshot("Krông Pắc, Đắk Lắk", plots), null);
    }

    public static PlotRefSnapshot plot() {
        return new PlotRefSnapshot(UUID.fromString("44444444-4444-4444-4444-444444444444"), "Hộ Y Bih",
                GeometryType.POLYGON, HASH_A, VerificationLevel.SELF_DECLARED);
    }

    public static ContractTemplateProfile template() {
        return new ContractTemplateProfile("VN_COFFEE_B2B", "1.0", HASH_B, "1.0");
    }

    public static ContractTermsSnapshot snapshot() {
        return ContractTermsSnapshot.assemble(input(), goods(), template(), RULES);
    }
}
