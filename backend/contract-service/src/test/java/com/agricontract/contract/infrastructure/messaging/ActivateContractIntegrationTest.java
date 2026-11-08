package com.agricontract.contract.infrastructure.messaging;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.exception.ActivationRefusedException;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.ActivateContractUseCase;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.contract.FrozenSchemas;
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

/**
 * #103 on real MySQL: escrow.deposit_locked through the consumer (dedup + transaction) to ACTIVE and
 * contract.activated in the outbox, once; refusals change nothing and are left for the DLQ (D40).
 */
@DataJpaTest(properties = "agricontract.eventing.outbox.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, EventingAutoConfiguration.class})
@Import({DepositLockedConsumer.class, ActivateContractUseCase.class, OutboxContractEventPublisher.class,
        ContractRepositoryImpl.class, JdbcSignatureRepository.class, ContractMapper.class,
        JcsTermsCanonicalizer.class, ContractTermsConfiguration.class, ConsentConfiguration.class})
@Testcontainers
class ActivateContractIntegrationTest {
    private static final String BUYER = "0b7e3b7a-1c1e-4c5e-9f0a-3d2b1a0c9e8f";
    private static final String SELLER = "5a4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a";
    private static final String LOCKED = "DEPOSIT_LOCKED";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired DepositLockedConsumer consumer;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;

    @BeforeEach
    void setUp() {
        contract = offered();
    }

    @Test
    void signedContract_becomesActive_andContractActivatedIsWrittenOnce() throws Exception {
        setStatus(ContractStatus.SIGNED);
        EventEnvelope signal = depositLocked(LOCKED, LOCKED);

        consumer.onDepositLocked(message(signal));

        assertThat(status()).isEqualTo(ContractStatus.ACTIVE);
        List<JsonNode> events = activatedEvents();
        assertThat(events).hasSize(1);
        JsonNode event = events.get(0);
        FrozenSchemas.assertEvent("contractActivated", event);
        assertThat(event.get("causationId").asText()).isEqualTo(signal.eventId().toString());
        assertThat(event.get("correlationId").asText()).isEqualTo(signal.correlationId());
        assertThat(event.at("/payload/listingId").asText()).isEqualTo(contract.getListingId());
        assertThat(event.at("/payload/reservedQuantity").decimalValue()).isEqualByComparingTo("2500.5");
    }

    @Test
    void sameSignalTwice_orANewSignalForAnActiveContract_isANoOp() throws Exception {
        setStatus(ContractStatus.SIGNED);
        EventEnvelope signal = depositLocked(LOCKED, LOCKED);
        consumer.onDepositLocked(message(signal));

        consumer.onDepositLocked(message(signal));
        consumer.onDepositLocked(message(depositLocked(LOCKED, LOCKED)));

        assertThat(status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(activatedEvents()).hasSize(1);
    }

    @Test
    void contractThatIsNotSigned_isRefused_andNothingIsRecorded() throws Exception {
        EventEnvelope signal = depositLocked(LOCKED, LOCKED);

        assertThatThrownBy(() -> consumer.onDepositLocked(message(signal)))
                .isInstanceOf(ActivationRefusedException.class);

        assertThat(status()).isEqualTo(ContractStatus.OFFERED);
        assertThat(activatedEvents()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class,
                signal.eventId().toString())).as("not marked processed, so a fixed contract can take it").isZero();
    }

    @Test
    void unknownContract_isRefused() {
        EventEnvelope signal = envelope(UUID.randomUUID().toString(), LOCKED, LOCKED);

        assertThatThrownBy(() -> consumer.onDepositLocked(message(signal)))
                .isInstanceOf(ActivationRefusedException.class);
    }

    @Test
    void sellerDepositAgreedButNotLocked_isRefused() throws Exception {
        // Fixture terms carry a 3% seller deposit, so a null seller state means a leg is missing.
        setStatus(ContractStatus.SIGNED);

        assertThatThrownBy(() -> consumer.onDepositLocked(message(depositLocked(LOCKED, null))))
                .isInstanceOf(ActivationRefusedException.class);
        assertThatThrownBy(() -> consumer.onDepositLocked(message(depositLocked("DEPOSIT_RELEASED", LOCKED))))
                .isInstanceOf(ActivationRefusedException.class);
        assertThat(status()).isEqualTo(ContractStatus.SIGNED);
    }

    @Test
    void payloadOutsideTheSchema_isRefusedBeforeAnything() {
        Map<String, Object> extra = new HashMap<>(depositLocked(LOCKED, LOCKED).payload());
        extra.put("note", "x");
        Map<String, Object> missing = new HashMap<>(depositLocked(LOCKED, LOCKED).payload());
        missing.remove("sellerDepositState");
        Map<String, Object> badState = new HashMap<>(depositLocked(LOCKED, LOCKED).payload());
        badState.put("buyerDepositState", "LOCKED");

        for (Map<String, Object> payload : List.of(extra, missing, badState)) {
            EventEnvelope bad = new EventEnvelope(UUID.randomUUID(), "escrow.deposit_locked", 1, Instant.now(),
                    "escrow-service", contract.getContractId().value(), UUID.randomUUID().toString(), null, null,
                    payload);
            assertThatThrownBy(() -> consumer.toCommand(bad)).isInstanceOf(InvalidEventPayloadException.class);
        }
        EventEnvelope otherProducer = new EventEnvelope(UUID.randomUUID(), "escrow.deposit_locked", 1,
                Instant.now(), "bank-service", "x", null, null, null, depositLocked(LOCKED, LOCKED).payload());
        assertThatThrownBy(() -> consumer.toCommand(otherProducer)).isInstanceOf(InvalidEventPayloadException.class);
    }

    private EventEnvelope depositLocked(String buyerState, String sellerState) {
        return envelope(contract.getContractId().value(), buyerState, sellerState);
    }

    private static EventEnvelope envelope(String contractId, String buyerState, String sellerState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("buyerDepositState", buyerState);
        payload.put("sellerDepositState", sellerState);
        return new EventEnvelope(UUID.randomUUID(), "escrow.deposit_locked", 1, Instant.now(), "escrow-service",
                contractId, contractId, UUID.randomUUID().toString(), null, payload);
    }

    private static Message message(EventEnvelope envelope) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(new EventEnvelopeCodec().toJson(envelope).getBytes(), properties);
    }

    private List<JsonNode> activatedEvents() {
        return jdbc.queryForList("SELECT envelope FROM event_outbox WHERE event_type = 'contract.activated'"
                        + " AND aggregate_id = ?", String.class, contract.getContractId().value()).stream()
                .map(json -> {
                    try {
                        return new ObjectMapper().readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    private void setStatus(ContractStatus status) {
        jdbc.update("UPDATE contracts SET status = ? WHERE contract_id = ?", status.name(),
                contract.getContractId().value());
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
