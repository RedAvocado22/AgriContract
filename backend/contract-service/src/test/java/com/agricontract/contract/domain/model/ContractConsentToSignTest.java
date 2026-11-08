package com.agricontract.contract.domain.model;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.consent.ConsentActionType;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.consent.ConsentStatement;
import com.agricontract.contract.domain.model.consent.ConsentStatements;
import com.agricontract.contract.domain.model.consent.ConsentSubjectType;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ContractConsentToSignTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final ConsentStatements STATEMENTS = new ConsentStatements(Map.of(
            SignerRole.BUYER, new ConsentStatement("SIGN_CONTRACT_AS_BUYER", "1.0", "buyer text", "c".repeat(64)),
            SignerRole.SELLER, new ConsentStatement("SIGN_CONTRACT_AS_SELLER", "1.0", "seller text", "d".repeat(64))),
            Map.of(SignerRole.BUYER, new ConsentStatement("CANCEL_ACTIVATION_AS_BUYER", "1.0", "b", "e".repeat(64)),
                    SignerRole.SELLER, new ConsentStatement("CANCEL_ACTIVATION_AS_SELLER", "1.0", "s", "f".repeat(64))));

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
    void sellerConsentIsBoundToStatementAndCurrentTerms() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Set.of());
        String hash = contract.getCoffeeTerms().signedContentHash();
        UUID id = UUID.randomUUID();

        ConsentEvidence e = contract.consentToSign(SELLER, SignerRole.SELLER, "1.0", hash, STATEMENTS, id, NOW, "corr-1");

        assertThat(e.consentId()).isEqualTo(id);
        assertThat(e.actionType()).isEqualTo(ConsentActionType.SIGN_CONTRACT);
        assertThat(e.subjectType()).isEqualTo(ConsentSubjectType.CONTRACT);
        assertThat(e.subjectId()).isEqualTo(contract.getContractId().value());
        assertThat(e.actorUserId()).isEqualTo(SELLER);
        assertThat(e.actorRole()).isEqualTo(SignerRole.SELLER);
        assertThat(e.statementCode()).isEqualTo("SIGN_CONTRACT_AS_SELLER");
        assertThat(e.statementHash()).isEqualTo("d".repeat(64));
        assertThat(e.presentedContentHash()).isEqualTo(hash);
        assertThat(e.consentedAt()).isEqualTo(NOW);
        assertThat(e.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void buyerGetsTheBuyerStatementAndConsentDoesNotChangeTheContract() {
        Contract contract = contract(ContractStatus.OFFERED, Set.of());

        ConsentEvidence e = consent(contract, BUYER, SignerRole.BUYER);

        assertThat(e.statementCode()).isEqualTo("SIGN_CONTRACT_AS_BUYER");
        assertThat(e.statementHash()).isEqualTo("c".repeat(64));
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        assertThat(contract.getSignedRoles()).isEmpty();
        assertThat(contract.pullDomainEvents()).isEmpty();
    }

    @Test
    void theOtherPartyMayConsentAfterTheFirstSignature() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Set.of(SELLER));

        assertThat(consent(contract, BUYER, SignerRole.BUYER).actorUserId()).isEqualTo(BUYER);
    }

    @Test
    void outsiderIsRefused() {
        Contract contract = contract(ContractStatus.OFFERED, Set.of());

        assertThatThrownBy(() -> consent(contract, "stranger", SignerRole.BUYER))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    @Test
    void partyClaimingTheOtherSideIsRefused() {
        Contract contract = contract(ContractStatus.OFFERED, Set.of());

        assertThatThrownBy(() -> consent(contract, BUYER, SignerRole.SELLER))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    @Test
    void outdatedStatementVersionIsRefused() {
        Contract contract = contract(ContractStatus.OFFERED, Set.of());
        String hash = contract.getCoffeeTerms().signedContentHash();

        assertConflict(() -> contract.consentToSign(SELLER, SignerRole.SELLER, "0.9", hash, STATEMENTS,
                UUID.randomUUID(), NOW, null), ContractConflictException.CONSENT_STATEMENT_OUTDATED);
    }

    @Test
    void staleContentHashIsRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Set.of());

        assertConflict(() -> contract.consentToSign(SELLER, SignerRole.SELLER, "1.0", HASH_A, STATEMENTS,
                UUID.randomUUID(), NOW, null), ContractConflictException.PRESENTED_CONTENT_STALE);
    }

    @Test
    void contractPastSigningIsRefused() {
        for (ContractStatus status : new ContractStatus[]{ContractStatus.SIGNED, ContractStatus.ACTIVE,
                ContractStatus.SETTLED}) {
            Contract contract = contract(status, Set.of());
            assertConflict(() -> consent(contract, SELLER, SignerRole.SELLER), ContractConflictException.NOT_SIGNABLE);
        }
    }

    @Test
    void partyThatAlreadySignedIsRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, Set.of(SELLER));

        assertConflict(() -> consent(contract, SELLER, SignerRole.SELLER), ContractConflictException.ALREADY_SIGNED);
    }

    @Test
    void phase1ContractWithoutCoffeeTermsIsRefused() {
        Contract legacy = Contract.reconstitute(new ContractId(UUID.randomUUID().toString()), "listing", BUYER,
                SELLER, "Cà phê", "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", null, null, ContractStatus.OFFERED,
                Map.of(), 0L);

        assertConflict(() -> legacy.consentToSign(SELLER, SignerRole.SELLER, "1.0", HASH_A, STATEMENTS,
                UUID.randomUUID(), NOW, null), ContractConflictException.LEGACY_TERMS);
    }

    private static ConsentEvidence consent(Contract contract, String user, SignerRole role) {
        return contract.consentToSign(user, role, "1.0", contract.getCoffeeTerms().signedContentHash(), STATEMENTS,
                UUID.randomUUID(), NOW, null);
    }

    private static void assertConflict(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(code);
    }

    /** signers: user ids that already signed these terms. */
    private static Contract contract(ContractStatus status, Set<String> signers) {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template(), RULES), FAKE);
        Map<SignerRole, String> signed = new EnumMap<>(SignerRole.class);
        signers.forEach(user -> signed.put(user.equals(BUYER) ? SignerRole.BUYER : SignerRole.SELLER,
                terms.signedContentHash()));
        return Contract.reconstitute(new ContractId(UUID.randomUUID().toString()), goods().listingId().toString(),
                BUYER, SELLER, "Cà phê", "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", null, terms, status,
                signed, 0L);
    }
}
