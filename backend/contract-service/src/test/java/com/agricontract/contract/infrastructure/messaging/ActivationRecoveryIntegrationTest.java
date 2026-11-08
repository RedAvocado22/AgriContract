package com.agricontract.contract.infrastructure.messaging;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.calendar.VietnamBusinessDays;
import com.agricontract.contract.application.dto.RetryDepositLockCommand;
import com.agricontract.contract.application.exception.ActivationRefusedException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.GetContractUseCase;
import com.agricontract.contract.application.usecase.RecordDepositLockFailureUseCase;
import com.agricontract.contract.application.usecase.RetryDepositLockUseCase;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
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
import com.agricontract.contract.infrastructure.persistence.activation.JdbcActivationRecoveryRepository;
import com.agricontract.contract.infrastructure.persistence.idempotency.JdbcIdempotencyStore;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcSignatureRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.EventingAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** #234, D42 on real MySQL: escrow.deposit_lock_failed in, recovery stored, notification and retry out. */
@DataJpaTest(properties = "agricontract.eventing.outbox.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, EventingAutoConfiguration.class})
@Import({DepositLockFailedConsumer.class, RecordDepositLockFailureUseCase.class, RetryDepositLockUseCase.class,
        GetContractUseCase.class, OutboxContractEventPublisher.class, JdbcActivationRecoveryRepository.class,
        JdbcIdempotencyStore.class, BusinessCalendarConfiguration.class, ContractRepositoryImpl.class,
        JdbcSignatureRepository.class, ContractMapper.class, JcsTermsCanonicalizer.class,
        ContractTermsConfiguration.class, ConsentConfiguration.class})
@Testcontainers
class ActivationRecoveryIntegrationTest {
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

    @Autowired DepositLockFailedConsumer consumer;
    @Autowired RetryDepositLockUseCase retry;
    @Autowired GetContractUseCase getContract;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired VietnamBusinessDays calendar;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;

    @BeforeEach
    void setUp() {
        contract = offered();
        setStatus(ContractStatus.SIGNED);
    }

    @Test
    void firstFailure_storesTheWindow_andNotifiesBothParties() throws Exception {
        EventEnvelope signal = lockFailed("SELLER");

        consumer.onDepositLockFailed(message(signal));

        Map<String, Object> row = recovery();
        Instant failedAt = ((Timestamp) row.get("deposit_lock_failed_at")).toInstant();
        assertThat(((Timestamp) row.get("one_sided_cancel_allowed_from")).toInstant())
                .isEqualTo(calendar.deadline(failedAt, 5).plusMillis(1));
        assertThat(row.get("last_failed_leg")).isEqualTo("SELLER");
        assertThat(status()).as("signatures stay: still SIGNED").isEqualTo(ContractStatus.SIGNED);

        List<JsonNode> notifications = outbox("notification.contract_activation_failed_requested");
        assertThat(notifications).hasSize(1);
        JsonNode command = notifications.get(0);
        FrozenSchemas.assertCommand("contractActivationFailedRequested", command);
        assertThat(command.at("/payload/recipients/0/email").asText()).isEqualTo("buyer@example.com");
        assertThat(command.at("/payload/recipients/1/role").asText()).isEqualTo("SELLER");
        assertThat(command.at("/payload/failedLeg").asText()).isEqualTo("SELLER");
        assertThat(command.at("/payload/nextActions/1").asText()).isEqualTo("REQUEST_ACTIVATION_CANCEL");
        assertThat(Instant.parse(command.at("/payload/oneSidedCancelAllowedFrom").asText()))
                .isEqualTo(calendar.deadline(failedAt, 5).plusMillis(1));
        assertThat(command.get("causationId").asText()).isEqualTo(signal.eventId().toString());
    }

    @Test
    void laterFailure_keepsTheFirstFailureAndItsWindow_andNotifiesAgain() throws Exception {
        consumer.onDepositLockFailed(message(lockFailed("SELLER")));
        Map<String, Object> first = recovery();

        consumer.onDepositLockFailed(message(lockFailed("BOTH")));

        Map<String, Object> second = recovery();
        assertThat(second.get("deposit_lock_failed_at")).isEqualTo(first.get("deposit_lock_failed_at"));
        assertThat(second.get("one_sided_cancel_allowed_from")).isEqualTo(first.get("one_sided_cancel_allowed_from"));
        assertThat(second.get("last_failed_leg")).isEqualTo("BOTH");
        assertThat(second.get("failure_reports")).isEqualTo(2);
        assertThat(outbox("notification.contract_activation_failed_requested")).hasSize(2);
    }

    @Test
    void sameReportTwice_isRecordedOnce() throws Exception {
        EventEnvelope signal = lockFailed("BUYER");
        consumer.onDepositLockFailed(message(signal));
        consumer.onDepositLockFailed(message(signal));

        assertThat(recovery().get("failure_reports")).isEqualTo(1);
        assertThat(outbox("notification.contract_activation_failed_requested")).hasSize(1);
    }

    @Test
    void activeContract_ignoresTheStaleReport_otherStatusesAreRefused() throws Exception {
        setStatus(ContractStatus.ACTIVE);
        consumer.onDepositLockFailed(message(lockFailed("SELLER")));
        assertThat(recoveryRows()).isZero();

        setStatus(ContractStatus.OFFERED);
        assertThatThrownBy(() -> consumer.onDepositLockFailed(message(lockFailed("SELLER"))))
                .isInstanceOf(ActivationRefusedException.class);
        assertThat(recoveryRows()).isZero();
        assertThat(outbox("notification.contract_activation_failed_requested")).isEmpty();
    }

    @Test
    void payloadOutsideTheSchema_isRefused() {
        Map<String, Object> badLeg = new HashMap<>(lockFailed("SELLER").payload());
        badLeg.put("failedLeg", "NONE");
        Map<String, Object> missing = new HashMap<>(lockFailed("SELLER").payload());
        missing.remove("reason");
        Map<String, Object> emptyReason = new HashMap<>(lockFailed("SELLER").payload());
        emptyReason.put("reason", "");
        for (Map<String, Object> payload : List.of(badLeg, missing, emptyReason)) {
            EventEnvelope bad = new EventEnvelope(UUID.randomUUID(), "escrow.deposit_lock_failed", 1, Instant.now(),
                    "escrow-service", contract.getContractId().value(), null, null, null, payload);
            assertThatThrownBy(() -> consumer.toCommand(bad)).isInstanceOf(InvalidEventPayloadException.class);
        }
    }

    @Test
    void operatorRetry_writesOneRetryRequest_andAReplayWritesNothing() throws Exception {
        consumer.onDepositLockFailed(message(lockFailed("SELLER")));
        Object window = recovery().get("one_sided_cancel_allowed_from");
        UUID key = UUID.randomUUID();

        retry.execute(new RetryDepositLockCommand(id(), OPERATOR, key));
        retry.execute(new RetryDepositLockCommand(id(), OPERATOR, key));

        List<JsonNode> requests = outbox("contract.deposit_lock_retry_requested");
        assertThat(requests).hasSize(1);
        FrozenSchemas.assertEvent("contractDepositLockRetryRequested", requests.get(0));
        assertThat(requests.get(0).at("/payload/operatorUserId").asText()).isEqualTo(OPERATOR);
        assertThat(recovery().get("one_sided_cancel_allowed_from")).as("a retry never moves the window")
                .isEqualTo(window);
        assertThatThrownBy(() -> retry.execute(new RetryDepositLockCommand(UUID.randomUUID().toString(), OPERATOR,
                key))).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void retry_isRefusedWhenTheLockHasNotFailed_orTheContractIsNotSigned() throws Exception {
        assertThatThrownBy(() -> retry.execute(new RetryDepositLockCommand(id(), OPERATOR, UUID.randomUUID())))
                .isInstanceOf(ContractConflictException.class)
                .hasFieldOrPropertyWithValue("code", ContractConflictException.ACTIVATION_NOT_FAILED);

        consumer.onDepositLockFailed(message(lockFailed("SELLER")));
        setStatus(ContractStatus.ACTIVE);
        assertThatThrownBy(() -> retry.execute(new RetryDepositLockCommand(id(), OPERATOR, UUID.randomUUID())))
                .isInstanceOf(ContractConflictException.class);
        assertThat(outbox("contract.deposit_lock_retry_requested")).isEmpty();
    }

    @Test
    void getContract_showsActivationRecoveryOnlyAfterAFailure() throws Exception {
        assertThat(getContract.execute(id(), BUYER, false).coffee().activationRecovery()).isNull();

        consumer.onDepositLockFailed(message(lockFailed("SELLER")));

        var recovery = getContract.execute(id(), BUYER, false).coffee().activationRecovery();
        assertThat(recovery.oneSidedCancelAllowedFrom())
                .isEqualTo(((Timestamp) recovery().get("one_sided_cancel_allowed_from")).toInstant());
        assertThat(recovery.cancelRequestedBy()).isEmpty();
    }

    private EventEnvelope lockFailed(String failedLeg) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", id());
        payload.put("failedLeg", failedLeg);
        payload.put("buyerDepositState", "SELLER".equals(failedLeg) ? "DEPOSIT_LOCKED" : "LOCK_FAILED");
        payload.put("sellerDepositState", "BUYER".equals(failedLeg) ? "DEPOSIT_LOCKED" : "LOCK_FAILED");
        payload.put("reason", failedLeg + ": SYSTEM_LOCK_ACTIVE");
        return new EventEnvelope(UUID.randomUUID(), "escrow.deposit_lock_failed", 1, Instant.now(), "escrow-service",
                id(), id(), UUID.randomUUID().toString(), null, payload);
    }

    private static Message message(EventEnvelope envelope) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(new EventEnvelopeCodec().toJson(envelope).getBytes(), properties);
    }

    private List<JsonNode> outbox(String eventType) {
        return jdbc.queryForList("SELECT envelope FROM event_outbox WHERE event_type = ? AND aggregate_id = ?"
                        + " ORDER BY created_at", String.class, eventType, id()).stream()
                .map(json -> {
                    try {
                        return new ObjectMapper().readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    private Map<String, Object> recovery() {
        return jdbc.queryForMap("SELECT * FROM activation_recovery WHERE contract_id = ?", id());
    }

    private int recoveryRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM activation_recovery WHERE contract_id = ?", Integer.class,
                id());
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
