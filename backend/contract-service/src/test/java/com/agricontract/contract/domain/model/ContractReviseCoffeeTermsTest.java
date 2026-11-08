package com.agricontract.contract.domain.model;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import com.agricontract.contract.domain.model.terms.PlotRefSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.terms.VndAmount;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractReviseCoffeeTermsTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";

    /** Stand-in for the JCS adapter: any deterministic text is enough for domain rules. */
    private static final TermsCanonicalizer FAKE = new TermsCanonicalizer() {
        @Override
        public String canonicalJson(ContractTermsSnapshot snapshot) {
            return snapshot.toString();
        }

        @Override
        public ContractTermsSnapshot parse(String canonicalJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String canonicalInputJson(ContractTermsInput input) {
            return input.toString();
        }
    };

    @Test
    void eitherPartyRevisesAndTheContractMovesToNegotiating() {
        Contract contract = offered();
        String before = contract.getCoffeeTerms().signedContentHash();

        contract.reviseCoffeeTerms(SELLER, terms(withPrice("96000.00")));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
        assertThat(contract.getCoffeeTerms().signedContentHash()).isNotEqualTo(before);
        assertThat(contract.getTerms().agreedPrice().amount()).isEqualByComparingTo("96000.00");

        contract.reviseCoffeeTerms(BUYER, terms(input()));
        assertThat(contract.getCoffeeTerms().signedContentHash()).isEqualTo(before);
    }

    @Test
    void outsiderCannotRevise() {
        assertThatThrownBy(() -> offered().reviseCoffeeTerms("someone-else", terms(input())))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    @Test
    void termsFreezeAtTheFirstSignature() {
        Contract contract = offered();
        sign(contract, BUYER);

        assertThatThrownBy(() -> contract.reviseCoffeeTerms(SELLER, terms(withPrice("96000.00"))))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(ContractConflictException.TERMS_FROZEN);
    }

    @Test
    void signedContractIsNotNegotiable() {
        Contract contract = offered();
        sign(contract, BUYER);
        sign(contract, SELLER);

        assertThatThrownBy(() -> contract.reviseCoffeeTerms(SELLER, terms(input())))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(ContractConflictException.NOT_NEGOTIABLE);
    }

    @Test
    void totalQuantityMustStayTheReservedQuantity() {
        ContractTermsInput t = input();
        List<MilestoneTerm> more = List.of(new MilestoneTerm(1, new BigDecimal("3000"), LocalDate.of(2026, 12, 15), 2));
        ContractTermsInput moreKg = new ContractTermsInput(t.agreedPrice(), t.currency(), more, t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());

        assertThatThrownBy(() -> offered().reviseCoffeeTerms(SELLER, terms(moreKg)))
                .isInstanceOf(InvalidContractTermsException.class)
                .hasMessageContaining("must stay 2500.5 kg");
    }

    @Test
    void reschedulingTheSameTotalIsAllowed() {
        ContractTermsInput t = input();
        List<MilestoneTerm> threeBatches = List.of(
                new MilestoneTerm(1, new BigDecimal("1000"), LocalDate.of(2026, 12, 15), 2),
                new MilestoneTerm(2, new BigDecimal("1000"), LocalDate.of(2027, 1, 10), 2),
                new MilestoneTerm(3, new BigDecimal("500.5"), LocalDate.of(2027, 2, 1), 2));
        ContractTermsInput rescheduled = new ContractTermsInput(t.agreedPrice(), t.currency(), threeBatches,
                t.qualityTerms(), t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(),
                t.toleranceRate(), t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());

        Contract contract = offered();
        contract.reviseCoffeeTerms(BUYER, terms(rescheduled));
        assertThat(contract.getCoffeeTerms().snapshot().negotiated().milestoneSchedule()).hasSize(3);
    }

    @Test
    void goodsCannotBeSwappedDuringNegotiation() {
        ContractTermsSnapshot otherGoods = ContractTermsSnapshot.assemble(input(), goods(List.of(
                new PlotRefSnapshot(UUID.randomUUID(), "Hộ khác",
                        plot().geometryType(), HASH_B, plot().verificationLevel()))), template(), RULES);

        assertThatThrownBy(() -> offered().reviseCoffeeTerms(SELLER, CanonicalTerms.of(otherGoods, FAKE)))
                .isInstanceOf(InvalidContractTermsException.class)
                .hasMessageContaining("goodsTerms are fixed");
    }

    @Test
    void phaseOneContractWithoutCoffeeTermsCannotBeRevised() {
        Contract legacy = Contract.offer(new ContractId("c-legacy"), "l-1", BUYER, SELLER, "Coffee", "B", "S",
                "b@x.test", "s@x.test", offered().getTerms());

        assertThatThrownBy(() -> legacy.reviseCoffeeTerms(SELLER, terms(input())))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(ContractConflictException.LEGACY_TERMS);
    }

    private static Contract offered() {
        return Contract.offerCoffee(new ContractId(UUID.randomUUID().toString()), goods().listingId().toString(),
                BUYER, SELLER, "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", terms(input()));
    }

    private static CanonicalTerms terms(ContractTermsInput input) {
        return CanonicalTerms.of(ContractTermsSnapshot.assemble(input, goods(), template(), RULES), FAKE);
    }

    static ContractTermsInput withPrice(String price) {
        ContractTermsInput t = input();
        return new ContractTermsInput(VndAmount.parse(price), t.currency(), t.milestoneSchedule(), t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());
    }

    /** Signs through the Phase 2 path: a verified challenge for the current terms. */
    private static void sign(Contract contract, String user) {
        SignerRole role = user.equals(BUYER) ? SignerRole.BUYER : SignerRole.SELLER;
        Instant now = Instant.now();
        OtpChallenge challenge = OtpChallenge.issue(UUID.randomUUID(), UUID.randomUUID(),
                contract.getContractId().value(), role, user, now, contract.getCoffeeTerms().signedContentHash(),
                "e".repeat(64), now, 300);
        contract.sign(user, challenge, now, now, null, UUID.randomUUID());
    }
}
