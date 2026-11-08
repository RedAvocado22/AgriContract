package com.agricontract.contract.infrastructure.signing;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.dto.VerifySignCommand;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.application.usecase.VerifyOtpAndSignUseCase;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.Signature;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import com.agricontract.contract.domain.repository.SignatureRepository;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.otp.OtpConfiguration;
import com.agricontract.contract.infrastructure.persistence.consent.JdbcConsentEvidenceRepository;
import com.agricontract.contract.infrastructure.persistence.idempotency.JdbcIdempotencyStore;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcOtpChallengeRepository;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcSignatureRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * VerifyOtpAndSign end to end on real MySQL, with codes issued by the real InitiateSign and a stubbed user-service:
 * what a right code, a wrong code, a replaced code, an ineligible party and two parties signing at once leave behind.
 */
@DataJpaTest(properties = {"contract.otp.resend-cooldown-seconds=0", "contract.otp.max-sends-per-hour=1000"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({VerifyOtpAndSignUseCase.class, InitiateSignUseCase.class, SignerEligibility.class, OtpChallengeIssuer.class,
        JdbcOtpChallengeRepository.class, JdbcSignatureRepository.class, OtpConfiguration.class,
        SigningConsentRecorder.class, JdbcConsentEvidenceRepository.class, ConsentConfiguration.class,
        JdbcIdempotencyStore.class, ContractRepositoryImpl.class, ContractMapper.class, JcsTermsCanonicalizer.class,
        ContractTermsConfiguration.class, VerifySignIntegrationTest.Stubs.class})
@Testcontainers
class VerifySignIntegrationTest {
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

    /** user-service answers per user (absent = no profile, null map = down); notification-service records emails. */
    @TestConfiguration
    static class Stubs {
        static final Map<String, UserInfo> profiles = new ConcurrentHashMap<>();
        static volatile boolean userServiceDown;
        static final List<OtpEmail> sent = Collections.synchronizedList(new ArrayList<>());

        @Bean
        UserPort userPort() {
            return new UserPort() {
                @Override
                public UserInfo getUser(String userId) {
                    return profiles.get(userId);
                }

                @Override
                public Optional<UserInfo> findUser(String userId) {
                    if (userServiceDown) {
                        throw new UserServiceUnavailableException("stub down", null);
                    }
                    return Optional.ofNullable(profiles.get(userId));
                }
            };
        }

        @Bean
        OtpEmailPort otpEmailPort() {
            return sent::add;
        }
    }

    @Autowired VerifyOtpAndSignUseCase verify;
    @Autowired InitiateSignUseCase initiate;
    @Autowired OtpChallengeRepository challenges;
    @Autowired SignatureRepository signatures;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;

    @BeforeEach
    void setUp() {
        Stubs.userServiceDown = false;
        Stubs.profiles.clear();
        Stubs.profiles.put(BUYER, profile(BUYER, null));
        Stubs.profiles.put(SELLER, profile(SELLER, null));
        Stubs.sent.clear();
        contract = offered();
    }

    @Test
    void rightCode_recordsTheSignature_andTheSecondPartyMakesTheContractSigned() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        Instant authTime = Instant.now().minusSeconds(10);

        verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(), authTime));

        Signature signature = signatures.findByContractId(id()).get(0);
        OtpChallenge challenge = challenges.findById(seller.otpId()).orElseThrow();
        assertThat(signature.signerRole()).isEqualTo(SignerRole.SELLER);
        assertThat(signature.signerUserId()).isEqualTo(SELLER);
        assertThat(signature.otpId()).isEqualTo(seller.otpId());
        assertThat(signature.consentId()).isEqualTo(challenge.consentId());
        assertThat(signature.signedContentHash()).isEqualTo(contract.getCoffeeTerms().signedContentHash());
        assertThat(signature.signedAt()).isEqualTo(challenge.verifiedAt());
        assertThat(signature.ipAddress()).isNull();
        assertThat(status()).isEqualTo(ContractStatus.OFFERED);

        Code buyer = codeFor(BUYER, SignerRole.BUYER);
        verify.execute(command(BUYER, UUID.randomUUID(), buyer, buyer.code(), Instant.now()));

        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
        assertThat(signatures.findByContractId(id())).extracting(Signature::signerRole)
                .containsExactlyInAnyOrder(SignerRole.SELLER, SignerRole.BUYER);
    }

    @Test
    void wrongCode_isCountedEvenThoughRefused_andTheSameKeyCanStillSign() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> verify.execute(command(SELLER, key, seller, wrong(seller), Instant.now())))
                .isInstanceOf(ContractConflictException.class)
                .hasMessageContaining("4 attempts left")
                .extracting("code").isEqualTo(VerifyOtpAndSignUseCase.OTP_INVALID);
        assertThat(challenges.findById(seller.otpId()).orElseThrow().attemptCount()).isEqualTo(1);
        assertThat(signatures.findByContractId(id())).isEmpty();

        verify.execute(command(SELLER, key, seller, seller.code(), Instant.now()));
        assertThat(signatures.findByContractId(id())).hasSize(1);
    }

    @Test
    void fifthWrongCode_locksTheChallenge_evenForTheRightCode() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, wrong(seller),
                    Instant.now()))).extracting("code").isEqualTo(VerifyOtpAndSignUseCase.OTP_INVALID);
        }

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, wrong(seller),
                Instant.now()))).extracting("code").isEqualTo(OtpChallenge.OTP_LOCKED);
        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now()))).extracting("code").isEqualTo(OtpChallenge.OTP_LOCKED);
        assertThat(signatures.findByContractId(id())).isEmpty();
    }

    @Test
    void sameKeyAfterSuccess_isAReplay_notASecondSignature() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        UUID key = UUID.randomUUID();
        verify.execute(command(SELLER, key, seller, seller.code(), Instant.now()));

        verify.execute(command(SELLER, key, seller, seller.code(), Instant.now()));

        assertThat(signatures.findByContractId(id())).hasSize(1);
    }

    @Test
    void sameKeyForAnotherCode_isRefused() {
        Code first = codeFor(SELLER, SignerRole.SELLER);
        UUID key = UUID.randomUUID();
        verify.execute(command(SELLER, key, first, first.code(), Instant.now()));

        Code other = new Code(UUID.randomUUID(), "123456");
        assertThatThrownBy(() -> verify.execute(command(SELLER, key, other, other.code(), Instant.now())))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void usedCodeWithANewKey_isRefused() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(), Instant.now()));

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now()))).extracting("code").isEqualTo(OtpChallenge.OTP_USED);
    }

    @Test
    void codeReplacedByAResend_isRefused_evenWhenRight() {
        Code first = codeFor(SELLER, SignerRole.SELLER);
        codeFor(SELLER, SignerRole.SELLER);

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), first, first.code(),
                Instant.now()))).extracting("code").isEqualTo(OtpChallenge.OTP_EXPIRED);
        assertThat(signatures.findByContractId(id())).isEmpty();
    }

    @Test
    void codeForTermsThatChanged_isKilled() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        jdbc.update("UPDATE signature_otp SET signed_content_hash = ? WHERE otp_id = ?", "0".repeat(64),
                seller.otpId().toString());

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now()))).extracting("code").isEqualTo(ContractConflictException.TERMS_CHANGED_SINCE_CODE);
        assertThat(challenges.findById(seller.otpId()).orElseThrow().isUsable(Instant.now(), 5)).isFalse();
        assertThat(signatures.findByContractId(id())).isEmpty();
    }

    @Test
    void someoneElsesCode_isRefused_withoutCountingAnAttempt() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);

        assertThatThrownBy(() -> verify.execute(command(BUYER, UUID.randomUUID(), seller, wrong(seller),
                Instant.now()))).isInstanceOf(UnauthorizedContractAccessException.class);
        assertThat(challenges.findById(seller.otpId()).orElseThrow().attemptCount()).isZero();
    }

    @Test
    void otherPartyLockedSinceTheyCouldSign_stopsTheSignature() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        Stubs.profiles.put(BUYER, profile(BUYER, Instant.now().plusSeconds(86400)));

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now()))).extracting("code").isEqualTo(ContractConflictException.COUNTERPARTY_NOT_ELIGIBLE);
        assertThat(challenges.findById(seller.otpId()).orElseThrow().verifiedAt()).isNull();
        assertThat(signatures.findByContractId(id())).isEmpty();
    }

    @Test
    void firstSignerLockedBeforeTheSecondSigns_contractStaysUnsigned() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(), Instant.now()));
        Code buyer = codeFor(BUYER, SignerRole.BUYER);
        Stubs.profiles.put(SELLER, profile(SELLER, Instant.now().plusSeconds(86400)));

        assertThatThrownBy(() -> verify.execute(command(BUYER, UUID.randomUUID(), buyer, buyer.code(),
                Instant.now()))).extracting("code").isEqualTo(ContractConflictException.COUNTERPARTY_NOT_ELIGIBLE);
        assertThat(status()).isEqualTo(ContractStatus.OFFERED);
    }

    @Test
    void signerLockedSinceTheCodeWasSent_isRefused() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        Stubs.profiles.put(SELLER, profile(SELLER, Instant.now().plusSeconds(86400)));

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now()))).isInstanceOf(SignerNotEligibleException.class)
                .extracting("code").isEqualTo(SignerNotEligibleException.LOCKED);
        assertThat(signatures.findByContractId(id())).isEmpty();
    }

    @Test
    void userServiceDown_failsClosed() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        Stubs.userServiceDown = true;

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now()))).isInstanceOf(UserServiceUnavailableException.class);
        assertThat(signatures.findByContractId(id())).isEmpty();
    }

    @Test
    void staleLogin_isRefused() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);

        assertThatThrownBy(() -> verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(),
                Instant.now().minusSeconds(301)))).extracting("code")
                .isEqualTo(SignerNotEligibleException.STEP_UP_REQUIRED);
    }

    @Test
    void bothPartiesAtOnce_bothSign_andTheContractIsSigned() throws Exception {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        Code buyer = codeFor(BUYER, SignerRole.BUYER);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> {
                start.await();
                verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(), Instant.now()));
                return null;
            });
            Future<?> b = pool.submit(() -> {
                start.await();
                verify.execute(command(BUYER, UUID.randomUUID(), buyer, buyer.code(), Instant.now()));
                return null;
            });
            start.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(signatures.findByContractId(id())).hasSize(2);
        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
    }

    @Test
    void signatureRows_cannotBeChangedOrDeleted() {
        Code seller = codeFor(SELLER, SignerRole.SELLER);
        verify.execute(command(SELLER, UUID.randomUUID(), seller, seller.code(), Instant.now()));

        assertThatThrownBy(() -> jdbc.update("UPDATE signature SET signer_user_id = 'x' WHERE contract_id = ?", id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM signature WHERE contract_id = ?", id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private record Code(UUID otpId, String code) {
    }

    /** Runs the real InitiateSign and picks the code out of the stubbed email. */
    private Code codeFor(String user, SignerRole role) {
        SignChallengeView view = initiate.execute(new InitiateSignCommand(id(), user, UUID.randomUUID(), role, "1.0",
                contract.getCoffeeTerms().signedContentHash(), Instant.now()));
        OtpEmail email = Stubs.sent.get(Stubs.sent.size() - 1);
        assertThat(email.requestId()).isEqualTo(view.otpId().toString());
        return new Code(view.otpId(), email.otpCode());
    }

    private static String wrong(Code code) {
        return code.code().equals("000000") ? "000001" : "000000";
    }

    private VerifySignCommand command(String user, UUID key, Code code, String typed, Instant authTime) {
        return new VerifySignCommand(id(), user, key, code.otpId(), typed, authTime, null);
    }

    private static UserInfo profile(String user, Instant lockedUntil) {
        return new UserInfo(user, "HTX " + user, user + "@x.test", user.startsWith("buyer") ? "BUYER" : "SELLER",
                "VERIFIED", null, lockedUntil);
    }

    private String id() {
        return contract.getContractId().value();
    }

    private ContractStatus status() {
        return contracts.findById(contract.getContractId()).orElseThrow().getStatus();
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
