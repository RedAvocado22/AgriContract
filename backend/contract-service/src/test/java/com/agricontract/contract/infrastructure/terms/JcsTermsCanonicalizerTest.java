package com.agricontract.contract.infrastructure.terms;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JcsTermsCanonicalizerTest {
    private final JcsTermsCanonicalizer canonicalizer = new JcsTermsCanonicalizer();

    @Test
    void sameTermsAlwaysGiveTheSameFingerprint() {
        assertThat(hash(snapshot())).isEqualTo(hash(snapshot()));
    }

    @Test
    void numberWritingDoesNotChangeTheFingerprint() {
        // 0.05 and 0.0500 are the same rate; RFC 8785 writes both as 0.05.
        ContractTermsInput t = input();
        ContractTermsInput padded = new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(),
                t.qualityTerms(), t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(),
                new BigDecimal("0.0500"), t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());
        assertThat(hash(ContractTermsSnapshot.assemble(padded, goods(), template(), RULES))).isEqualTo(hash(snapshot()));
    }

    @Test
    void changingAnyTermChangesTheFingerprint() {
        ContractTermsInput t = input();
        ContractTermsInput otherDeposit = new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(),
                t.qualityTerms(), t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(),
                t.toleranceRate(), t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), new BigDecimal("0.06"),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());
        assertThat(hash(ContractTermsSnapshot.assemble(otherDeposit, goods(), template(), RULES)))
                .isNotEqualTo(hash(snapshot()));
    }

    /** Matrix 11t: the template and quality profile are part of what is signed. */
    @Test
    void changingOnlyTheTemplateOrQualityProfileChangesTheFingerprint() {
        String base = hash(snapshot());
        ContractTemplateProfile t = template();
        assertThat(hash(withTemplate(new ContractTemplateProfile(t.contractTemplateCode(), "1.1",
                t.contractTemplateHash(), t.coffeeQualityProfileVersion())))).isNotEqualTo(base);
        assertThat(hash(withTemplate(new ContractTemplateProfile(t.contractTemplateCode(),
                t.contractTemplateVersion(), "c".repeat(64), t.coffeeQualityProfileVersion())))).isNotEqualTo(base);
        assertThat(hash(withTemplate(new ContractTemplateProfile(t.contractTemplateCode(),
                t.contractTemplateVersion(), t.contractTemplateHash(), "1.1")))).isNotEqualTo(base);
    }

    @Test
    void wireShapeIsFlatSortedAndKeepsNullKeys() throws Exception {
        String json = canonicalizer.canonicalJson(snapshot());
        ObjectNode wire = (ObjectNode) new ObjectMapper().readTree(json);

        // No whitespace between tokens: re-printing compactly gives the identical text.
        assertThat(new ObjectMapper().writeValueAsString(wire)).isEqualTo(json);
        assertThat(json).startsWith("{\"agreedPrice\":\"95000.00\",\"buyerConfirmWindowDays\":3,");
        assertThat(wire.get("totalContractValue").textValue()).isEqualTo("237547500.00");
        assertThat(wire.get("contractTemplateCode").textValue()).isEqualTo("VN_COFFEE_B2B");
        assertThat(wire.has("level2InspectorOrg")).isTrue();
        assertThat(wire.get("level2InspectorOrg").isNull()).isTrue();
        assertThat(wire.get("goodsTerms").has("traceabilitySnapshot")).isTrue();
        assertThat(wire.get("goodsTerms").get("traceabilitySnapshot").isNull()).isTrue();
        assertThat(wire.get("milestoneSchedule").get(0).get("expectedDeliveryDate").textValue())
                .isEqualTo("2026-12-15");
    }

    @Test
    void storedTermsReadBackToTheSameFingerprint() {
        CanonicalTerms stored = CanonicalTerms.of(snapshot(), canonicalizer);
        CanonicalTerms restored = CanonicalTerms.restore(stored.canonicalJson(), stored.signedContentHash(), canonicalizer);

        assertThat(restored.signedContentHash()).isEqualTo(stored.signedContentHash());
        assertThat(canonicalizer.canonicalJson(restored.snapshot())).isEqualTo(stored.canonicalJson());
        assertThat(restored.snapshot().totalContractValue()).isEqualTo(snapshot().totalContractValue());
    }

    @Test
    void editedStoredTermsAreRefused() {
        CanonicalTerms stored = CanonicalTerms.of(snapshot(), canonicalizer);
        String edited = stored.canonicalJson().replace("\"95000.00\"", "\"95001.00\"");

        assertThatThrownBy(() -> CanonicalTerms.restore(edited, stored.signedContentHash(), canonicalizer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("do not match");
    }

    /**
     * A later code version that adds a field would serialise differently, but stored terms are verified from
     * their stored text, so their fingerprint stays valid.
     */
    @Test
    void aNewFieldInLaterCodeDoesNotBreakStoredFingerprints() {
        CanonicalTerms stored = CanonicalTerms.of(snapshot(), canonicalizer);
        TermsCanonicalizer laterVersion = new TermsCanonicalizer() {
            @Override
            public String canonicalJson(ContractTermsSnapshot snapshot) {
                return canonicalizer.canonicalJson(snapshot).replaceFirst("^\\{", "{\"aNewField\":null,");
            }

            @Override
            public ContractTermsSnapshot parse(String json) {
                return canonicalizer.parse(json);
            }
        };

        assertThat(CanonicalTerms.of(snapshot(), laterVersion).signedContentHash())
                .isNotEqualTo(stored.signedContentHash());
        assertThat(CanonicalTerms.restore(stored.canonicalJson(), stored.signedContentHash(), laterVersion)
                .signedContentHash()).isEqualTo(stored.signedContentHash());
    }

    private String hash(ContractTermsSnapshot snapshot) {
        return CanonicalTerms.of(snapshot, canonicalizer).signedContentHash();
    }

    private static ContractTermsSnapshot withTemplate(ContractTemplateProfile template) {
        return ContractTermsSnapshot.assemble(input(), goods(), template, RULES);
    }
}
