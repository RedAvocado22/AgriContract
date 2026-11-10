package com.agricontract.contract.infrastructure.withdraw;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.dto.VerifySignCommand;
import com.agricontract.contract.application.dto.WithdrawOfferCommand;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.application.usecase.VerifyOtpAndSignUseCase;
import com.agricontract.contract.application.usecase.WithdrawOfferUseCase;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.contract.FrozenSchemas;
import com.agricontract.contract.infrastructure.messaging.OutboxContractEventPublisher;
import com.agricontract.contract.infrastructure.otp.OtpConfiguration;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
 * WITHDRAW_OFFER (#233) on real MySQL: the three outbox rows (checked against the frozen schemas), replay, the
 * refusal after the second signature, the pending OTPs killed, and a withdraw racing the second signature.
 */
@DataJpaTest(properties = {"contract.otp.resend-cooldown-seconds=0", "contract.otp.max-sends-per-hour=1000",
        "agricontract.eventing.outbox.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, EventingAutoConfiguration.class})
@Import({WithdrawOfferUseCase.class, InitiateSignUseCase.class, VerifyOtpAndSignUseCase.class,
        SignerEligibility.class, OtpChallengeIssuer.class, SigningConsentRecorder.class,
        JdbcOtpChallengeRepository.class, JdbcSignatureRepository.class, JdbcConsentEvidenceRepository.class,
        JdbcIdempotencyStore.class, OtpConfiguration.class, ConsentConfiguration.class, ContractRepositoryImpl.class,
        ContractMapper.class, JcsTermsCanonicalizer.class, ContractTermsConfiguration.class,
        OutboxContractEventPublisher.class, WithdrawOfferIntegrationTest.Stubs.class})
@Testcontainers
class WithdrawOfferIntegrationTest {
    private static final String BUYER = "0b7e3b7a-1c1e-4c5e-9f0a-3d2b1a0c9e8f";
    private static final String SELLER = "5a4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

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

    @Autowired WithdrawOfferUseCase withdraw;
    @Autowired InitiateSignUseCase initiateSign;
    @Autowired VerifyOtpAndSignUseCase verifySign;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;

    @BeforeEach
    void setUp() {
        Stubs.profiles.put(BUYER, new UserInfo(BUYER, "HTX Mua", "buyer@x.test", "BUYER", "VERIFIED", null, null));
        Stubs.profiles.put(SELLER, new UserInfo(SELLER, "HTX Bán", "seller@x.test", "SELLER", "VERIFIED", null,
                null));
        Stubs.sent.clear();
        contract = offered();
    }

    @Test
    void buyerWithdraws_contractEnds_andTheThreeRecordsAreWrittenOnce() throws Exception {
        withdraw.execute(new WithdrawOfferCommand(id(), BUYER, UUID.randomUUID()));

        assertThat(status()).isEqualTo(ContractStatus.WITHDRAWN);
        List<JsonNode> withdrawn = outbox("contract.withdrawn");
        assertThat(withdrawn).hasSize(1);
        FrozenSchemas.assertEvent("contractWithdrawn", withdrawn.get(0));
        List<JsonNode> terminated = outbox("contract.terminated");
        assertThat(terminated).hasSize(1);
        FrozenSchemas.assertEvent("contractTerminated", terminated.get(0));
        assertThat(terminated.get(0).at("/payload/terminationType").asText()).isEqualTo("WITHDRAW_OFFER");
        assertThat(terminated.get(0).at("/payload/requestedBy").asText()).isEqualTo("BUYER");
        List<JsonNode> notices = outbox("notification.contract_terminated_requested");
        assertThat(notices).hasSize(1);
        FrozenSchemas.assertCommand("contractTerminatedRequested", notices.get(0));
        assertThat(notices.get(0).at("/payload/recipients/0/role").asText()).as("the other party").isEqualTo("SELLER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id = ?", Integer.class,
                id())).as("no remedy.finalized or any other record").isEqualTo(3);
    }

    @Test
    void sameKeyAgain_isA204Replay_newKeyOnAWithdrawnOffer_is409() {
        UUID key = UUID.randomUUID();
        withdraw.execute(new WithdrawOfferCommand(id(), SELLER, key));

        withdraw.execute(new WithdrawOfferCommand(id(), SELLER, key));
        assertThat(outbox("contract.withdrawn")).as("written once").hasSize(1);

        assertThatThrownBy(() -> withdraw.execute(new WithdrawOfferCommand(id(), SELLER, UUID.randomUUID())))
                .hasFieldOrPropertyWithValue("code", ContractConflictException.NOT_WITHDRAWABLE);
        assertThat(outbox("contract.withdrawn")).hasSize(1);
    }

    @Test
    void thePartyThatSignedFirst_withdraws_andTheOthersPendingCodeIsKilled() {
        sign(SELLER, SignerRole.SELLER);
        SignChallengeView buyerCode = initiateSign.execute(new InitiateSignCommand(id(), BUYER, UUID.randomUUID(),
                SignerRole.BUYER, "1.0", hash(), Instant.now()));
        String code = lastEmail().otpCode();

        withdraw.execute(new WithdrawOfferCommand(id(), SELLER, UUID.randomUUID()));

        // Compared with a JDBC timestamp, as the column is written (JVM time zone), not with MySQL NOW().
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM signature_otp WHERE contract_id = ?"
                + " AND verified_at IS NULL AND expires_at > ?", Integer.class, id(),
                Timestamp.from(Instant.now()))).isZero();
        assertThatThrownBy(() -> verifySign.execute(new VerifySignCommand(id(), BUYER, UUID.randomUUID(),
                buyerCode.otpId(), code, Instant.now(), null))).isInstanceOf(ContractConflictException.class);
        assertThat(status()).isEqualTo(ContractStatus.WITHDRAWN);
        assertThat(signatures()).isEqualTo(1);
        assertThat(outbox("contract.signed")).isEmpty();
    }

    @Test
    void afterTheSecondSignature_withdrawIsRefused_andNothingIsWritten() {
        sign(SELLER, SignerRole.SELLER);
        sign(BUYER, SignerRole.BUYER);

        assertThatThrownBy(() -> withdraw.execute(new WithdrawOfferCommand(id(), BUYER, UUID.randomUUID())))
                .hasFieldOrPropertyWithValue("code", ContractConflictException.NOT_WITHDRAWABLE);
        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
        assertThat(outbox("contract.withdrawn")).isEmpty();
        assertThat(outbox("contract.terminated")).isEmpty();
    }

    @Test
    void someoneElse_isRefused_andTheKeyIsNotSpent() {
        String stranger = "7c6b5a49-3827-4615-a4b3-c2d1e0f9a8b7";
        assertThatThrownBy(() -> withdraw.execute(new WithdrawOfferCommand(id(), stranger, UUID.randomUUID())))
                .isInstanceOf(UnauthorizedContractAccessException.class);
        assertThat(status()).isEqualTo(ContractStatus.OFFERED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE actor_id = ?", Integer.class,
                stranger)).isZero();
    }

    /** Withdraw and the second signature at once: the row lock lets exactly one of them happen. */
    @RepeatedTest(5)
    void withdrawRacingTheSecondSignature_exactlyOneWins() throws Exception {
        sign(SELLER, SignerRole.SELLER);
        SignChallengeView buyerCode = initiateSign.execute(new InitiateSignCommand(id(), BUYER, UUID.randomUUID(),
                SignerRole.BUYER, "1.0", hash(), Instant.now()));
        String code = lastEmail().otpCode();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> signed = pool.submit(() -> {
                start.await();
                try {
                    verifySign.execute(new VerifySignCommand(id(), BUYER, UUID.randomUUID(), buyerCode.otpId(), code,
                            Instant.now(), null));
                    return true;
                } catch (ContractConflictException refused) {
                    return false;
                }
            });
            Future<Boolean> withdrawn = pool.submit(() -> {
                start.await();
                try {
                    withdraw.execute(new WithdrawOfferCommand(id(), SELLER, UUID.randomUUID()));
                    return true;
                } catch (ContractConflictException refused) {
                    return false;
                }
            });
            start.countDown();
            boolean signWon = signed.get(30, TimeUnit.SECONDS);
            boolean withdrawWon = withdrawn.get(30, TimeUnit.SECONDS);

            assertThat(signWon ^ withdrawWon).as("exactly one wins").isTrue();
            if (signWon) {
                assertThat(status()).isEqualTo(ContractStatus.SIGNED);
                assertThat(outbox("contract.signed")).hasSize(1);
                assertThat(outbox("contract.withdrawn")).isEmpty();
            } else {
                assertThat(status()).isEqualTo(ContractStatus.WITHDRAWN);
                assertThat(signatures()).isEqualTo(1);
                assertThat(outbox("contract.signed")).isEmpty();
                assertThat(outbox("contract.withdrawn")).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void sign(String user, SignerRole role) {
        SignChallengeView challenge = initiateSign.execute(new InitiateSignCommand(id(), user, UUID.randomUUID(),
                role, "1.0", hash(), Instant.now()));
        verifySign.execute(new VerifySignCommand(id(), user, UUID.randomUUID(), challenge.otpId(),
                lastEmail().otpCode(), Instant.now(), null));
    }

    private OtpEmail lastEmail() {
        return Stubs.sent.get(Stubs.sent.size() - 1);
    }

    private int signatures() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM signature WHERE contract_id = ?", Integer.class, id());
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
        return contracts.findById(contract.getContractId()).orElseThrow().getCoffeeTerms().signedContentHash();
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
                goods().listingId().toString(), BUYER, SELLER, "HTX Mua", "HTX Bán", "buyer@example.com",
                "seller@example.com", terms);
        contracts.save(offered);
        return offered;
    }
}
