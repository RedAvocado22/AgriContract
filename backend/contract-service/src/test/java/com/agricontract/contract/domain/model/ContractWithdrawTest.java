package com.agricontract.contract.domain.model;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** WITHDRAW_OFFER (#233): either party, before the second signature, terminal, no money. */
class ContractWithdrawTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";

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

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"OFFERED", "NEGOTIATING"})
    void eitherParty_withdrawsAnOfferNotSignedByBoth(ContractStatus status) {
        Contract byBuyer = contract(status, Map.of());
        Contract bySeller = contract(status, Map.of());

        assertThat(byBuyer.withdraw(BUYER)).isEqualTo(SignerRole.BUYER);
        assertThat(bySeller.withdraw(SELLER)).isEqualTo(SignerRole.SELLER);
        assertThat(byBuyer.getStatus()).isEqualTo(ContractStatus.WITHDRAWN);
        assertThat(bySeller.getStatus()).isEqualTo(ContractStatus.WITHDRAWN);
    }

    @Test
    void thePartyThatAlreadySigned_canStillWithdraw() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of(SignerRole.SELLER, "a".repeat(64)));

        assertThat(contract.withdraw(SELLER)).isEqualTo(SignerRole.SELLER);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.WITHDRAWN);
    }

    @Test
    void theOtherParty_canWithdrawAfterTheFirstSignature() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of(SignerRole.SELLER, "a".repeat(64)));

        assertThat(contract.withdraw(BUYER)).isEqualTo(SignerRole.BUYER);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.WITHDRAWN);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"OFFERED", "NEGOTIATING"}, mode = EnumSource.Mode.EXCLUDE)
    void afterTheSecondSignatureOrOnceEnded_isRefused(ContractStatus status) {
        Contract contract = contract(status, Map.of());

        assertThatThrownBy(() -> contract.withdraw(BUYER))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(ContractConflictException.NOT_WITHDRAWABLE);
        assertThat(contract.getStatus()).isEqualTo(status);
    }

    @Test
    void someoneElse_isRefused_andNothingChanges() {
        Contract contract = contract(ContractStatus.OFFERED, Map.of());

        assertThatThrownBy(() -> contract.withdraw("stranger-1"))
                .isInstanceOf(UnauthorizedContractAccessException.class);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
    }

    @Test
    void aPhase1Row_withoutCoffeeTerms_isRefused() {
        Contract contract = Contract.reconstitute(new ContractId(UUID.randomUUID().toString()),
                goods().listingId().toString(), BUYER, SELLER, "Cà phê", "HTX Mua", "HTX Bán", "b@x.test",
                "s@x.test", null, null, ContractStatus.OFFERED, Map.of(), 0L);

        assertThatThrownBy(() -> contract.withdraw(BUYER))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(ContractConflictException.LEGACY_TERMS);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
    }

    private static Contract contract(ContractStatus status, Map<SignerRole, String> signed) {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template(), RULES), FAKE);
        return Contract.reconstitute(new ContractId(UUID.randomUUID().toString()), goods().listingId().toString(),
                BUYER, SELLER, "Cà phê", "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", null, terms, status,
                signed, 0L);
    }
}
