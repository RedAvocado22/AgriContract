package com.agricontract.contract.domain.model;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.Signature;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ContractSignTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

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
    void firstSignature_isRecordedForTheChallengeAndTheCurrentTerms_contractNotSignedYet() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of());
        OtpChallenge challenge = challenge(contract, SignerRole.SELLER, SELLER, hashOf(contract));
        UUID signatureId = UUID.randomUUID();

        Signature signature = contract.sign(SELLER, challenge, NOW.minusSeconds(30), NOW, null, signatureId);

        assertThat(signature).isEqualTo(new Signature(signatureId, contract.getContractId().value(),
                SignerRole.SELLER, SELLER, challenge.otpId(), challenge.consentId(), NOW.minusSeconds(30), NOW,
                hashOf(contract), null));
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
        assertThat(contract.getSignedRoles()).containsExactly(SignerRole.SELLER);
    }

    @Test
    void secondSignatureOnTheSameTerms_makesTheContractSigned() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of());
        contract.sign(SELLER, challenge(contract, SignerRole.SELLER, SELLER, hashOf(contract)), NOW, NOW, null,
                UUID.randomUUID());

        contract.sign(BUYER, challenge(contract, SignerRole.BUYER, BUYER, hashOf(contract)), NOW, NOW, null,
                UUID.randomUUID());

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        assertThat(contract.getSignedRoles()).isEqualTo(Set.of(SignerRole.BUYER, SignerRole.SELLER));
    }

    @Test
    void secondSignatureOnOtherTermsThanTheFirst_isRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of(SignerRole.SELLER, "f".repeat(64)));

        assertThatThrownBy(() -> contract.sign(BUYER, challenge(contract, SignerRole.BUYER, BUYER, hashOf(contract)),
                NOW, NOW, null, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
    }

    @Test
    void samePartyTwice_isRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of());
        contract.sign(SELLER, challenge(contract, SignerRole.SELLER, SELLER, hashOf(contract)), NOW, NOW, null,
                UUID.randomUUID());

        assertConflict(() -> contract.sign(SELLER, challenge(contract, SignerRole.SELLER, SELLER, hashOf(contract)),
                NOW, NOW, null, UUID.randomUUID()), ContractConflictException.ALREADY_SIGNED);
    }

    @Test
    void challengeForOtherTerms_isRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of());

        assertConflict(() -> contract.sign(SELLER, challenge(contract, SignerRole.SELLER, SELLER, "0".repeat(64)),
                NOW, NOW, null, UUID.randomUUID()), ContractConflictException.TERMS_CHANGED_SINCE_CODE);
        assertThat(contract.getSignedRoles()).isEmpty();
    }

    @Test
    void callerWhoIsNotThatParty_isRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Map.of());

        assertThatThrownBy(() -> contract.sign(BUYER, challenge(contract, SignerRole.SELLER, BUYER, hashOf(contract)),
                NOW, NOW, null, UUID.randomUUID()))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    @Test
    void signedContract_cannotBeSignedAgain() {
        Contract contract = contract(ContractStatus.SIGNED, Map.of());

        assertConflict(() -> contract.sign(SELLER, challenge(contract, SignerRole.SELLER, SELLER, hashOf(contract)),
                NOW, NOW, null, UUID.randomUUID()), ContractConflictException.NOT_SIGNABLE);
    }

    @Test
    void roleOf_namesTheParty_orNothing() {
        Contract contract = contract(ContractStatus.OFFERED, Map.of());

        assertThat(contract.roleOf(BUYER)).contains(SignerRole.BUYER);
        assertThat(contract.roleOf(SELLER)).contains(SignerRole.SELLER);
        assertThat(contract.roleOf("someone-else")).isEmpty();
    }

    private static String hashOf(Contract contract) {
        return contract.getCoffeeTerms().signedContentHash();
    }

    private static OtpChallenge challenge(Contract contract, SignerRole role, String user, String hash) {
        return OtpChallenge.issue(UUID.randomUUID(), UUID.randomUUID(), contract.getContractId().value(), role, user,
                NOW, hash, "e".repeat(64), NOW, 300);
    }

    private static void assertConflict(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(code);
    }

    private static Contract contract(ContractStatus status, Map<SignerRole, String> signed) {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template(), RULES), FAKE);
        return Contract.reconstitute(new ContractId(UUID.randomUUID().toString()), goods().listingId().toString(),
                BUYER, SELLER, "Cà phê", "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", null, terms, status,
                signed, 0L);
    }
}
