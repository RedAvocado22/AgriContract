package com.agricontract.contract.infrastructure.activation;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.dto.ActivationCancelCommands;
import com.agricontract.contract.application.dto.DepositLockFailedCommand;
import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.dto.RetryDepositLockCommand;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.dto.VerifySignCommand;
import com.agricontract.contract.application.exception.ActivationRefusedException;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.ActivationRefundStarter;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.application.usecase.ActivationCancelUseCases;
import com.agricontract.contract.application.usecase.CompleteActivationFailureUseCase;
import com.agricontract.contract.application.usecase.GetContractUseCase;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.application.usecase.RecordDepositLockFailureUseCase;
import com.agricontract.contract.application.usecase.RetryDepositLockUseCase;
import com.agricontract.contract.application.usecase.StartDueOneSidedCancelsUseCase;
import com.agricontract.contract.application.usecase.VerifyOtpAndSignUseCase;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.config.BusinessCalendarConfiguration;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.contract.FrozenSchemas;
import com.agricontract.contract.infrastructure.messaging.OutboxContractEventPublisher;
import com.agricontract.contract.infrastructure.otp.OtpConfiguration;
import com.agricontract.contract.infrastructure.persistence.activation.JdbcActivationCancelRequestRepository;
import com.agricontract.contract.infrastructure.persistence.activation.JdbcActivationRecoveryRepository;
import com.agricontract.contract.infrastructure.persistence.consent.JdbcConsentEvidenceRepository;
import com.agricontract.contract.infrastructure.persistence.idempotency.JdbcIdempotencyStore;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcOtpChallengeRepository;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcSignatureRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import com.agricontract.eventing.EventingAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * D42 on real MySQL: the parties' cancel requests (OTP bound to CANCEL_ACTIVATION), the one-sided window, withdrawal,
 * and the end of the branch (escrow.activation_refund_completed → ACTIVATION_FAILED). Every outbox row is checked
 * against the frozen schemas.
 */
@DataJpaTest(properties = {"contract.otp.resend-cooldown-seconds=0", "contract.otp.max-sends-per-hour=1000",
        "agricontract.eventing.outbox.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, EventingAutoConfiguration.class})
@Import({ActivationCancelUseCases.class, ActivationRefundStarter.class, StartDueOneSidedCancelsUseCase.class,
        CompleteActivationFailureUseCase.class, RecordDepositLockFailureUseCase.class, RetryDepositLockUseCase.class,
        GetContractUseCase.class, InitiateSignUseCase.class, VerifyOtpAndSignUseCase.class, SignerEligibility.class,
        OtpChallengeIssuer.class, SigningConsentRecorder.class, JdbcOtpChallengeRepository.class,
        JdbcSignatureRepository.class, JdbcConsentEvidenceRepository.class, JdbcActivationRecoveryRepository.class,
        JdbcActivationCancelRequestRepository.class, JdbcIdempotencyStore.class, OtpConfiguration.class,
        ConsentConfiguration.class, BusinessCalendarConfiguration.class, ContractRepositoryImpl.class,
        ContractMapper.class, JcsTermsCanonicalizer.class, ContractTermsConfiguration.class,
        OutboxContractEventPublisher.class, ActivationCancelIntegrationTest.Stubs.class})
@Testcontainers
class ActivationCancelIntegrationTest {
    private static final String BUYER = "0b7e3b7a-1c1e-4c5e-9f0a-3d2b1a0c9e8f";
    private static final String SELLER = "5a4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a";
    private static final String OPERATOR = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /** user-service answers from a map (a party may be locked: cancelling must still work); emails are recorded. */
    @TestConfiguration
    static class Stubs {
        static final Map<String, UserInfo> profiles = new ConcurrentHashMap<>();
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
                    return Optional.ofNullable(profiles.get(userId));
                }
            };
        }

        @Bean
        OtpEmailPort otpEmailPort() {
            return sent::add;
        }
    }

    @Autowired ActivationCancelUseCases cancel;
    @Autowired StartDueOneSidedCancelsUseCase startDue;
    @Autowired CompleteActivationFailureUseCase complete;
    @Autowired RecordDepositLockFailureUseCase recordFailure;
    @Autowired RetryDepositLockUseCase retry;
    @Autowired GetContractUseCase getContract;
    @Autowired InitiateSignUseCase initiateSign;
    @Autowired VerifyOtpAndSignUseCase verifySign;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;

    @BeforeEach
    void setUp() {
        Stubs.profiles.put(BUYER, new UserInfo(BUYER, "HTX Mua", "buyer@x.test", "BUYER", "VERIFIED", null, null));
        // A locked seller can still cancel: getting the money back is never blocked by eligibility.
        Stubs.profiles.put(SELLER, new UserInfo(SELLER, "HTX Bán", "seller@x.test", "SELLER", "VERIFIED", null,
                Instant.now().plusSeconds(86_400)));
        Stubs.sent.clear();
        contract = offered();
    }

    @Test
    void bothPartiesRequest_refundStartsAtOnce() throws Exception {
        lockFailed();

        request(BUYER, SignerRole.BUYER);
        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
        List<JsonNode> notices = outbox("notification.activation_cancel_requested");
        assertThat(notices).hasSize(1);
        FrozenSchemas.assertCommand("activationCancelRequested", notices.get(0));
        assertThat(notices.get(0).at("/payload/recipients/0/role").asText()).as("the other party").isEqualTo("SELLER");
        assertThat(lastEmail().purpose()).isEqualTo(OtpEmail.CANCEL_ACTIVATION);

        request(SELLER, SignerRole.SELLER);

        assertThat(status()).isEqualTo(ContractStatus.ACTIVATION_REFUND_PENDING);
        List<JsonNode> refunds = outbox("contract.activation_refund_requested");
        assertThat(refunds).hasSize(1);
        FrozenSchemas.assertEvent("contractActivationRefundRequested", refunds.get(0));
        assertThat(refunds.get(0).at("/payload/trigger").asText()).isEqualTo("BOTH_PARTIES");
        assertThat(refunds.get(0).at("/payload/requestedBy").toString()).isEqualTo("[\"BUYER\",\"SELLER\"]");
    }

    @Test
    void onePartyBeforeTheWindow_waits_thenTheJobStartsTheRefundWhenItPasses() throws Exception {
        lockFailed();
        request(SELLER, SignerRole.SELLER);
        assertThat(startDue.execute()).as("window not reached").isZero();
        assertThat(getContract.execute(id(), BUYER, false).coffee().activationRecovery().cancelRequestedBy())
                .containsExactly("SELLER");

        moveWindowToThePast();

        assertThat(startDue.execute()).isEqualTo(1);
        assertThat(status()).isEqualTo(ContractStatus.ACTIVATION_REFUND_PENDING);
        JsonNode refund = outbox("contract.activation_refund_requested").get(0);
        assertThat(refund.at("/payload/trigger").asText()).isEqualTo("ONE_PARTY_AFTER_WINDOW");
        assertThat(startDue.execute()).as("once").isZero();
    }

    @Test
    void onePartyAfterTheWindow_startsTheRefundOnVerify() throws Exception {
        lockFailed();
        moveWindowToThePast();

        request(BUYER, SignerRole.BUYER);

        assertThat(status()).isEqualTo(ContractStatus.ACTIVATION_REFUND_PENDING);
        assertThat(outbox("notification.activation_cancel_requested")).as("no notice, the refund started").isEmpty();
    }

    @Test
    void withdrawnRequest_isNotCounted_andCanBeMadeAgain() throws Exception {
        lockFailed();
        request(SELLER, SignerRole.SELLER);

        cancel.withdraw(new ActivationCancelCommands.Withdraw(id(), SELLER, UUID.randomUUID()));
        moveWindowToThePast();
        assertThat(startDue.execute()).isZero();
        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
        assertThatThrownBy(() -> cancel.withdraw(new ActivationCancelCommands.Withdraw(id(), SELLER,
                UUID.randomUUID()))).hasFieldOrPropertyWithValue("code", ContractConflictException.NO_CANCEL_REQUEST);

        request(SELLER, SignerRole.SELLER);
        assertThat(status()).isEqualTo(ContractStatus.ACTIVATION_REFUND_PENDING);
    }

    @Test
    void cancelIsRefusedBeforeALockFailure_andTwiceForTheSameParty() throws Exception {
        setStatus(ContractStatus.SIGNED);
        assertThatThrownBy(() -> initiate(BUYER, SignerRole.BUYER))
                .hasFieldOrPropertyWithValue("code", ContractConflictException.NOT_CANCELLABLE);

        lockFailed();
        request(BUYER, SignerRole.BUYER);
        assertThatThrownBy(() -> initiate(BUYER, SignerRole.BUYER))
                .hasFieldOrPropertyWithValue("code", ContractConflictException.CANCEL_ALREADY_REQUESTED);
    }

    @Test
    void wrongCode_isCounted_andNoRequestIsRecorded() throws Exception {
        lockFailed();
        SignChallengeView challenge = initiate(BUYER, SignerRole.BUYER);

        assertThatThrownBy(() -> cancel.verify(new ActivationCancelCommands.Verify(id(), BUYER, UUID.randomUUID(),
                challenge.otpId(), "000000".equals(lastEmail().otpCode()) ? "111111" : "000000", Instant.now())))
                .hasFieldOrPropertyWithValue("code", VerifyOtpAndSignUseCase.OTP_INVALID);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM signature_otp WHERE otp_id = ?", Integer.class,
                challenge.otpId().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activation_cancel_request WHERE contract_id = ?",
                Integer.class, id())).isZero();
    }

    @Test
    void aSigningCodeNeverCancels_andACancelCodeNeverSigns() throws Exception {
        SignChallengeView signing = initiateSign.execute(new InitiateSignCommand(id(), BUYER, UUID.randomUUID(),
                SignerRole.BUYER, "1.0", hash(), Instant.now()));
        String signingCode = lastEmail().otpCode();
        lockFailed();
        assertThatThrownBy(() -> cancel.verify(new ActivationCancelCommands.Verify(id(), BUYER, UUID.randomUUID(),
                signing.otpId(), signingCode, Instant.now())))
                .hasFieldOrPropertyWithValue("code", VerifyOtpAndSignUseCase.OTP_NOT_FOUND);

        SignChallengeView cancelling = initiate(SELLER, SignerRole.SELLER);
        String cancelCode = lastEmail().otpCode();
        // Unlocked here so signing reaches the code check (a locked signer is refused before it).
        Stubs.profiles.put(SELLER, new UserInfo(SELLER, "HTX Bán", "seller@x.test", "SELLER", "VERIFIED", null, null));
        assertThatThrownBy(() -> verifySign.execute(new VerifySignCommand(id(), SELLER, UUID.randomUUID(),
                cancelling.otpId(), cancelCode, Instant.now(), null)))
                .hasFieldOrPropertyWithValue("code", VerifyOtpAndSignUseCase.OTP_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM signature WHERE contract_id = ?", Integer.class, id()))
                .isZero();
    }

    @Test
    void refundCompleted_endsTheContractAsActivationFailed_once() throws Exception {
        lockFailed();
        request(BUYER, SignerRole.BUYER);
        request(SELLER, SignerRole.SELLER);
        String eventId = UUID.randomUUID().toString();

        tx.executeWithoutResult(s -> complete.execute(id(), eventId, id()));
        tx.executeWithoutResult(s -> complete.execute(id(), UUID.randomUUID().toString(), id()));

        assertThat(status()).isEqualTo(ContractStatus.ACTIVATION_FAILED);
        List<JsonNode> failed = outbox("contract.activation_failed");
        assertThat(failed).hasSize(1);
        FrozenSchemas.assertEvent("contractActivationFailed", failed.get(0));
        assertThat(failed.get(0).get("causationId").asText()).isEqualTo(eventId);
        assertThatThrownBy(() -> retry.execute(new RetryDepositLockCommand(id(), OPERATOR, UUID.randomUUID())))
                .hasFieldOrPropertyWithValue("code", ContractConflictException.ACTIVATION_NOT_FAILED);
    }

    @Test
    void refundCompletedForAContractNotWaitingForIt_isRefused() {
        setStatus(ContractStatus.SIGNED);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> complete.execute(id(), UUID.randomUUID().toString(),
                id()))).isInstanceOf(ActivationRefusedException.class);
        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
    }

    @Test
    void afterTheRefundStarted_nothingCanBeWithdrawnOrRetried() throws Exception {
        lockFailed();
        moveWindowToThePast();
        request(BUYER, SignerRole.BUYER);

        assertThatThrownBy(() -> cancel.withdraw(new ActivationCancelCommands.Withdraw(id(), BUYER,
                UUID.randomUUID()))).hasFieldOrPropertyWithValue("code", ContractConflictException.NOT_CANCELLABLE);
        assertThatThrownBy(() -> retry.execute(new RetryDepositLockCommand(id(), OPERATOR, UUID.randomUUID())))
                .hasFieldOrPropertyWithValue("code", ContractConflictException.ACTIVATION_NOT_FAILED);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void lockFailed() {
        setStatus(ContractStatus.SIGNED);
        tx.executeWithoutResult(s -> recordFailure.execute(new DepositLockFailedCommand(UUID.randomUUID().toString(),
                id(), id(), "SELLER", "DEPOSIT_LOCKED", "LOCK_FAILED", "SELLER: SYSTEM_LOCK_ACTIVE")));
    }

    private SignChallengeView initiate(String user, SignerRole role) {
        return cancel.initiate(new ActivationCancelCommands.Initiate(id(), user, UUID.randomUUID(), role, "1.0",
                hash(), Instant.now()));
    }

    private void request(String user, SignerRole role) {
        SignChallengeView challenge = initiate(user, role);
        cancel.verify(new ActivationCancelCommands.Verify(id(), user, UUID.randomUUID(), challenge.otpId(),
                lastEmail().otpCode(), Instant.now()));
    }

    private OtpEmail lastEmail() {
        return Stubs.sent.get(Stubs.sent.size() - 1);
    }

    private void moveWindowToThePast() {
        jdbc.update("UPDATE activation_recovery SET deposit_lock_failed_at = ?, one_sided_cancel_allowed_from = ?"
                        + " WHERE contract_id = ?", Timestamp.from(Instant.now().minusSeconds(86_400 * 10)),
                Timestamp.from(Instant.now().minusSeconds(1)), id());
    }

    private List<JsonNode> outbox(String eventType) {
        return jdbc.queryForList("SELECT envelope FROM event_outbox WHERE event_type = ? AND aggregate_id = ?"
                + " ORDER BY id", String.class, eventType, id()).stream().map(json -> {
                    try {
                        return new ObjectMapper().readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    private String hash() {
        return contract.getCoffeeTerms().signedContentHash();
    }

    private String id() {
        return contract.getContractId().value();
    }

    private void setStatus(ContractStatus status) {
        jdbc.update("UPDATE contracts SET status = ? WHERE contract_id = ?", status.name(), id());
    }

    private ContractStatus status() {
        return contracts.findById(contract.getContractId()).orElseThrow().getStatus();
    }

    private Contract offered() {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template, rules),
                canonicalizer);
        Contract offered = Contract.offerCoffee(new ContractId(UUID.randomUUID().toString()),
                goods().listingId().toString(), BUYER, SELLER, "HTX Mua", "HTX Bán", "buyer@example.com",
                "seller@example.com", terms);
        contracts.save(offered);
        return offered;
    }
}
