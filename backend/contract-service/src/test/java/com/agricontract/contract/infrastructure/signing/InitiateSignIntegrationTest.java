package com.agricontract.contract.infrastructure.signing;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.port.OtpCodes;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.OtpPolicy;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.otp.OtpConfiguration;
import com.agricontract.contract.infrastructure.persistence.consent.JdbcConsentEvidenceRepository;
import com.agricontract.contract.infrastructure.persistence.idempotency.JdbcIdempotencyStore;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcOtpChallengeRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * InitiateSign end to end on real MySQL with stubbed user-service and notification-service: what is stored, what
 * is emailed, and what a failed email, a replay, the send limits and an ineligible signer leave behind.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({InitiateSignUseCase.class, SignerEligibility.class, OtpChallengeIssuer.class,
        JdbcOtpChallengeRepository.class, OtpConfiguration.class, SigningConsentRecorder.class,
        JdbcConsentEvidenceRepository.class, ConsentConfiguration.class, JdbcIdempotencyStore.class,
        ContractRepositoryImpl.class, ContractMapper.class, JcsTermsCanonicalizer.class,
        ContractTermsConfiguration.class, InitiateSignIntegrationTest.Stubs.class})
@Testcontainers
class InitiateSignIntegrationTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /** Stand-ins for user-service (the profile to return) and notification-service (records or refuses). */
    @TestConfiguration
    static class Stubs {
        static final AtomicReference<UserInfo> profile = new AtomicReference<>();
        static final List<OtpEmail> sent = new ArrayList<>();
        static final AtomicReference<String> emailFailure = new AtomicReference<>();

        @Bean
        UserPort userPort() {
            return new UserPort() {
                @Override
                public UserInfo getUser(String userId) {
                    return profile.get();
                }

                @Override
                public Optional<UserInfo> findUser(String userId) {
                    return Optional.ofNullable(profile.get());
                }
            };
        }

        @Bean
        OtpEmailPort otpEmailPort() {
            return email -> {
                if (emailFailure.get() != null) {
                    throw new OtpEmailNotSentException(emailFailure.get(), "stub");
                }
                sent.add(email);
            };
        }
    }

    @Autowired InitiateSignUseCase useCase;
    @Autowired OtpChallengeRepository challenges;
    @Autowired OtpCodes otpCodes;
    @Autowired OtpPolicy policy;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;

    @BeforeEach
    void setUp() {
        Stubs.profile.set(new UserInfo(SELLER, "HTX Bán", "seller.profile@x.test", "SELLER", "VERIFIED", null, null));
        Stubs.sent.clear();
        Stubs.emailFailure.set(null);
        contract = offered();
    }

    @Test
    void codeGoesToTheProfileEmail_boundToTheChallengeAndTheCurrentTerms() {
        UUID key = UUID.randomUUID();

        SignChallengeView view = useCase.execute(command(key));

        OtpChallenge stored = challenges.findById(view.otpId()).orElseThrow();
        assertThat(view.expiresAt()).isEqualTo(stored.expiresAt());
        assertThat(stored.isUsable(Instant.now(), policy.maxAttempts())).isTrue();
        assertThat(Stubs.sent).hasSize(1);
        OtpEmail email = Stubs.sent.get(0);
        assertThat(email.requestId()).isEqualTo(view.otpId().toString());
        assertThat(email.recipientUserId()).isEqualTo(SELLER);
        assertThat(email.recipientEmail()).isEqualTo("seller.profile@x.test");
        assertThat(email.signedContentHash()).isEqualTo(contract.getCoffeeTerms().signedContentHash());
        assertThat(email.expiresAt()).isEqualTo(stored.expiresAt());
        assertThat(otpCodes.matches(email.otpCode(), stored.otpHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT correlation_id FROM consent_evidence WHERE consent_id = ?",
                String.class, stored.consentId().toString())).isEqualTo(key.toString());
    }

    @Test
    void emailNotSent_killsTheNewCode_keepsTheConsent() {
        Stubs.emailFailure.set("OTP_EMAIL_PROVIDER_FAILED");

        assertThatThrownBy(() -> useCase.execute(command(UUID.randomUUID())))
                .isInstanceOf(OtpEmailNotSentException.class)
                .extracting("code").isEqualTo("OTP_EMAIL_PROVIDER_FAILED");

        assertThat(usableCodes()).isZero();
        assertThat(rows("signature_otp")).isEqualTo(1);
        assertThat(rows("consent_evidence")).isEqualTo(1);
    }

    @Test
    void sameKeyAgain_returnsTheSameChallenge_withoutASecondEmail() {
        UUID key = UUID.randomUUID();
        SignChallengeView first = useCase.execute(command(key));

        SignChallengeView again = useCase.execute(command(key));

        assertThat(again).isEqualTo(first);
        assertThat(Stubs.sent).hasSize(1);
        assertThat(rows("signature_otp")).isEqualTo(1);
        assertThat(rows("consent_evidence")).isEqualTo(1);
    }

    @Test
    void sameKeyAgainAfterTheEmailFailed_asksForANewRequest() {
        UUID key = UUID.randomUUID();
        Stubs.emailFailure.set("OTP_EMAIL_TIMEOUT");
        assertThatThrownBy(() -> useCase.execute(command(key))).isInstanceOf(OtpEmailNotSentException.class);
        Stubs.emailFailure.set(null);

        assertThatThrownBy(() -> useCase.execute(command(key)))
                .isInstanceOf(ContractConflictException.class)
                .extracting("code").isEqualTo(InitiateSignUseCase.CHALLENGE_NOT_ACTIVE);
        assertThat(Stubs.sent).isEmpty();
    }

    @Test
    void sameKeyForAnotherRequest_isRefused() {
        UUID key = UUID.randomUUID();
        useCase.execute(command(key));

        assertThatThrownBy(() -> useCase.execute(new InitiateSignCommand(contract.getContractId().value(), SELLER,
                key, SignerRole.SELLER, "1.0", "0".repeat(64), Instant.now())))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void newCodeWithinTheCooldown_isRefused_andTheEarlierCodeStaysUsable() {
        SignChallengeView first = useCase.execute(command(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.execute(command(UUID.randomUUID())))
                .isInstanceOf(ContractConflictException.class)
                .extracting("code").isEqualTo(OtpPolicy.RESEND_TOO_SOON);

        assertThat(challenges.findById(first.otpId()).orElseThrow().isUsable(Instant.now(), policy.maxAttempts()))
                .isTrue();
        assertThat(rows("signature_otp")).isEqualTo(1);
        assertThat(rows("consent_evidence")).isEqualTo(1);
        assertThat(Stubs.sent).hasSize(1);
    }

    @Test
    void newCodeAfterTheCooldown_replacesTheEarlierOne() {
        SignChallengeView first = useCase.execute(command(UUID.randomUUID()));
        ageSentCodes(61);

        SignChallengeView second = useCase.execute(command(UUID.randomUUID()));

        assertThat(second.otpId()).isNotEqualTo(first.otpId());
        assertThat(challenges.findById(first.otpId()).orElseThrow().isUsable(Instant.now(), policy.maxAttempts()))
                .isFalse();
        assertThat(usableCodes()).isEqualTo(1);
    }

    @Test
    void sixthCodeWithinAnHour_isRefused() {
        for (int i = 0; i < 5; i++) {
            useCase.execute(command(UUID.randomUUID()));
            ageSentCodes(61);
        }

        assertThatThrownBy(() -> useCase.execute(command(UUID.randomUUID())))
                .isInstanceOf(ContractConflictException.class)
                .extracting("code").isEqualTo(OtpPolicy.RESEND_LIMIT);
        assertThat(Stubs.sent).hasSize(5);
    }

    @Test
    void theOtherPartysCodes_doNotCountTowardsTheLimits() {
        useCase.execute(command(UUID.randomUUID()));
        Stubs.profile.set(new UserInfo(BUYER, "HTX Mua", "buyer.profile@x.test", "BUYER", "VERIFIED", null, null));

        SignChallengeView buyer = useCase.execute(new InitiateSignCommand(contract.getContractId().value(), BUYER,
                UUID.randomUUID(), SignerRole.BUYER, "1.0", contract.getCoffeeTerms().signedContentHash(),
                Instant.now()));

        assertThat(challenges.findById(buyer.otpId())).isPresent();
        assertThat(Stubs.sent).extracting(OtpEmail::recipientEmail)
                .containsExactly("seller.profile@x.test", "buyer.profile@x.test");
    }

    @Test
    void ineligibleSigner_leavesNoTrace() {
        Stubs.profile.set(new UserInfo(SELLER, "HTX Bán", "s@x.test", "SELLER", "VERIFIED", null,
                Instant.now().plusSeconds(86400)));

        assertThatThrownBy(() -> useCase.execute(command(UUID.randomUUID())))
                .isInstanceOf(SignerNotEligibleException.class)
                .extracting("code").isEqualTo(SignerNotEligibleException.LOCKED);
        assertNothingStored();
    }

    @Test
    void staleLogin_leavesNoTrace() {
        assertThatThrownBy(() -> useCase.execute(new InitiateSignCommand(contract.getContractId().value(), SELLER,
                UUID.randomUUID(), SignerRole.SELLER, "1.0", contract.getCoffeeTerms().signedContentHash(),
                Instant.now().minusSeconds(301))))
                .isInstanceOf(SignerNotEligibleException.class)
                .extracting("code").isEqualTo(SignerNotEligibleException.STEP_UP_REQUIRED);
        assertNothingStored();
    }

    @Test
    void termsChangedSinceThePageLoaded_leavesNoTrace() {
        assertThatThrownBy(() -> useCase.execute(new InitiateSignCommand(contract.getContractId().value(), SELLER,
                UUID.randomUUID(), SignerRole.SELLER, "1.0", "0".repeat(64), Instant.now())))
                .isInstanceOf(ContractConflictException.class);
        assertNothingStored();
    }

    private InitiateSignCommand command(UUID key) {
        return new InitiateSignCommand(contract.getContractId().value(), SELLER, key, SignerRole.SELLER, "1.0",
                contract.getCoffeeTerms().signedContentHash(), Instant.now());
    }

    /** Moves every code of this contract back in time, as if they had been sent that many seconds earlier. */
    private void ageSentCodes(int seconds) {
        jdbc.update("UPDATE signature_otp SET sent_at = sent_at - INTERVAL ? SECOND WHERE contract_id = ?",
                seconds, contract.getContractId().value());
    }

    private int usableCodes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM signature_otp WHERE contract_id = ?"
                        + " AND verified_at IS NULL AND expires_at > ? AND attempt_count < 5",
                Integer.class, contract.getContractId().value(), Timestamp.from(Instant.now()));
    }

    private int rows(String table) {
        String column = table.equals("consent_evidence") ? "subject_id" : "contract_id";
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class,
                contract.getContractId().value());
    }

    private void assertNothingStored() {
        assertThat(rows("signature_otp")).isZero();
        assertThat(rows("consent_evidence")).isZero();
        assertThat(Stubs.sent).isEmpty();
    }

    private Contract offered() {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template, rules),
                canonicalizer);
        Contract offered = Contract.offerCoffee(new ContractId(UUID.randomUUID().toString()),
                goods().listingId().toString(), BUYER, SELLER, "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", terms);
        contracts.save(offered);
        return offered;
    }
}
