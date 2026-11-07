package com.agricontract.contract.infrastructure.otp;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.dto.IssuedOtp;
import com.agricontract.contract.application.port.OtpCodes;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.persistence.consent.JdbcConsentEvidenceRepository;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcOtpChallengeRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real MySQL, real commits: one usable challenge per signer and content, also under concurrent resends. */
// Resends here come milliseconds apart: the send limits have their own tests (InitiateSignIntegrationTest).
@DataJpaTest(properties = {"contract.otp.resend-cooldown-seconds=0", "contract.otp.max-sends-per-hour=1000"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({OtpChallengeIssuer.class, JdbcOtpChallengeRepository.class, OtpConfiguration.class,
        SigningConsentRecorder.class, JdbcConsentEvidenceRepository.class, ConsentConfiguration.class,
        ContractRepositoryImpl.class, ContractMapper.class, JcsTermsCanonicalizer.class,
        ContractTermsConfiguration.class})
@Testcontainers
class OtpChallengeIntegrationTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";
    private static final Instant AUTH_TIME = Instant.parse("2026-11-07T09:59:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired OtpChallengeIssuer issuer;
    @Autowired SigningConsentRecorder consents;
    @Autowired OtpChallengeRepository challenges;
    @Autowired OtpCodes otpCodes;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @Test
    void challengeIsStoredBoundAndWithoutThePlainCode() {
        Contract contract = offered();

        IssuedOtp issued = initiate(contract, SELLER, SignerRole.SELLER);

        OtpChallenge stored = challenges.findById(issued.challenge().otpId()).orElseThrow();
        assertThat(stored).isEqualTo(issued.challenge());
        assertThat(stored.contractId()).isEqualTo(contract.getContractId().value());
        assertThat(stored.userId()).isEqualTo(SELLER);
        assertThat(stored.signerRole()).isEqualTo(SignerRole.SELLER);
        assertThat(stored.authTime()).isEqualTo(AUTH_TIME);
        assertThat(stored.signedContentHash()).isEqualTo(contract.getCoffeeTerms().signedContentHash());
        assertThat(stored.expiresAt()).isEqualTo(stored.sentAt().plusSeconds(300));
        assertThat(stored.otpHash()).isNotEqualTo(issued.code());
        assertThat(otpCodes.matches(issued.code(), stored.otpHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT presented_content_hash FROM consent_evidence WHERE consent_id = ?",
                String.class, stored.consentId().toString())).isEqualTo(stored.signedContentHash());
    }

    @Test
    void resendKillsTheOlderChallengeEvenWithTheRightCode() {
        Contract contract = offered();
        IssuedOtp first = initiate(contract, SELLER, SignerRole.SELLER);

        IssuedOtp second = initiate(contract, SELLER, SignerRole.SELLER);

        OtpChallenge old = challenges.findById(first.challenge().otpId()).orElseThrow();
        Instant afterResend = second.challenge().sentAt();
        assertThat(old.isUsable(afterResend, 5)).isFalse();
        assertThatThrownBy(() -> old.ensureUsable(afterResend, 5)).hasMessageContaining("expired or was replaced");
        assertThat(otpCodes.matches(first.code(), old.otpHash())).isTrue();
        assertThat(usable(contract, SELLER)).isEqualTo(1);
    }

    @Test
    void resendDoesNotTouchTheOtherPartysChallenge() {
        Contract contract = offered();
        IssuedOtp buyers = initiate(contract, BUYER, SignerRole.BUYER);

        initiate(contract, SELLER, SignerRole.SELLER);
        initiate(contract, SELLER, SignerRole.SELLER);

        assertThat(challenges.findById(buyers.challenge().otpId()).orElseThrow()
                .isUsable(Instant.now(), 5)).isTrue();
        assertThat(usable(contract, BUYER)).isEqualTo(1);
        assertThat(usable(contract, SELLER)).isEqualTo(1);
    }

    @Test
    void concurrentResendsLeaveExactlyOneUsableChallenge() throws Exception {
        Contract contract = offered();
        int racers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IssuedOtp>> results = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return initiate(contract, SELLER, SignerRole.SELLER);
            }));
        }
        start.countDown();
        for (Future<IssuedOtp> f : results) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM signature_otp WHERE contract_id = ?", Integer.class,
                contract.getContractId().value())).isEqualTo(racers);
        assertThat(usable(contract, SELLER)).isEqualTo(1);
    }

    @Test
    void expireKillsTheChallengeWhenTheEmailFails() {
        Contract contract = offered();
        IssuedOtp issued = initiate(contract, SELLER, SignerRole.SELLER);

        issuer.expire(issued.challenge().otpId());

        assertThat(challenges.findById(issued.challenge().otpId()).orElseThrow()
                .isUsable(Instant.now(), 5)).isFalse();
        assertThat(usable(contract, SELLER)).isZero();
    }

    @Test
    void consentForAnotherContractIsRefused() {
        Contract contract = offered();
        Contract other = offered();
        ConsentEvidence otherConsent = tx.execute(s -> consents.record(other, SELLER, SignerRole.SELLER, "1.0",
                other.getCoffeeTerms().signedContentHash(), null));

        assertThatThrownBy(() -> tx.execute(s -> issuer.issue(contract, otherConsent, AUTH_TIME)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM signature_otp WHERE contract_id = ?", Integer.class,
                contract.getContractId().value())).isZero();
    }

    private IssuedOtp initiate(Contract contract, String user, SignerRole role) {
        return tx.execute(s -> {
            ConsentEvidence consent = consents.record(contract, user, role, "1.0",
                    contract.getCoffeeTerms().signedContentHash(), null);
            return issuer.issue(contract, consent, AUTH_TIME);
        });
    }

    /** Usable right now, by the same rule the service applies (instant passed from Java like the service does). */
    private int usable(Contract contract, String user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM signature_otp WHERE contract_id = ? AND user_id = ?"
                        + " AND verified_at IS NULL AND expires_at > ? AND attempt_count < 5",
                Integer.class, contract.getContractId().value(), user, Timestamp.from(Instant.now()));
    }

    private Contract offered() {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template, rules),
                canonicalizer);
        Contract contract = Contract.offerCoffee(new ContractId(UUID.randomUUID().toString()),
                goods().listingId().toString(), BUYER, SELLER, "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", terms);
        contracts.save(contract);
        return contract;
    }
}
