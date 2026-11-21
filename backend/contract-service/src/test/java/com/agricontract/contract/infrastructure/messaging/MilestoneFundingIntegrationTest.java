package com.agricontract.contract.infrastructure.messaging;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.exception.MilestoneFundingRefusedException;
import com.agricontract.contract.application.port.TransactionRunner;
import com.agricontract.contract.application.service.MilestoneFunding;
import com.agricontract.contract.application.usecase.RecordMilestoneFundingResultUseCase;
import com.agricontract.contract.application.usecase.RequestDueMilestoneFundingUseCase;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.milestone.FundingStatus;
import com.agricontract.contract.domain.model.milestone.Milestone;
import com.agricontract.contract.domain.model.milestone.MilestoneStatus;
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
import com.agricontract.contract.infrastructure.persistence.SpringTransactionRunner;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.milestone.JdbcMilestoneRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * #119 on MySQL: the daily funding schedule (D64) and escrow's funding results, through the real repository, outbox
 * and V16 constraints.
 */
@DataJpaTest(properties = "agricontract.eventing.outbox.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, EventingAutoConfiguration.class})
@Import({BusinessCalendarConfiguration.class, SpringTransactionRunner.class, MilestoneFunding.class,
        JdbcMilestoneRepository.class, RequestDueMilestoneFundingUseCase.class,
        RecordMilestoneFundingResultUseCase.class, MilestoneFundingResultConsumer.class,
        OutboxContractEventPublisher.class, ContractRepositoryImpl.class, JdbcSignatureRepository.class,
        ContractMapper.class, JcsTermsCanonicalizer.class, ContractTermsConfiguration.class,
        ConsentConfiguration.class})
@Testcontainers
class MilestoneFundingIntegrationTest {
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

    @Autowired RequestDueMilestoneFundingUseCase requestDue;
    @Autowired MilestoneFundingResultConsumer consumer;
    @Autowired MilestoneFunding funding;
    @Autowired JdbcMilestoneRepository milestones;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TransactionRunner transactions;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    private Contract contract;
    private Milestone first;
    private Milestone second;

    /** An ACTIVE contract with its two milestones planned and none due (fixture delivery dates are in 2036-2037). */
    @BeforeEach
    void setUp() {
        contract = offered();
        jdbc.update("UPDATE contracts SET status = 'ACTIVE' WHERE contract_id = ?", id());
        Contract active = contracts.findById(contract.getContractId()).orElseThrow();
        List<Milestone> planned = transactions.inTransaction(
                () -> funding.planOnActivation(active, Instant.now(), id(), null));
        first = planned.get(0);
        second = planned.get(1);
    }

    @Test
    void aMilestoneWhoseDateHasCome_isRequestedOnce_andTheOthersWait() throws Exception {
        makeDue(first);

        assertThat(requestDue.execute()).isEqualTo(1);
        assertThat(requestDue.execute()).as("not requested twice").isZero();

        assertThat(reload(first).fundingStatus()).isEqualTo(FundingStatus.FUNDING_PENDING);
        assertThat(reload(first).batchAmount().wire()).isEqualTo("95000000.00");
        assertThat(reload(second).fundingStatus()).isEqualTo(FundingStatus.NOT_REQUESTED);
        List<JsonNode> requests = fundingRequests();
        assertThat(requests).hasSize(1);
        FrozenSchemas.assertEvent("milestoneFundingRequested", requests.get(0));
        assertThat(requests.get(0).at("/payload/milestoneId").asText()).isEqualTo(first.milestoneId());
        assertThat(requests.get(0).get("correlationId").asText()).isEqualTo(id());
    }

    @Test
    void eachMilestoneFollowsItsOwnDate_withoutWaitingForThePreviousOne() {
        makeDue(second);

        assertThat(requestDue.execute()).isEqualTo(1);

        assertThat(reload(first).fundingStatus()).isEqualTo(FundingStatus.NOT_REQUESTED);
        assertThat(reload(second).fundingStatus()).isEqualTo(FundingStatus.FUNDING_PENDING);
        assertThat(reload(second).batchAmount().wire()).isEqualTo("142547500.00");
    }

    @Test
    void aContractThatIsNoLongerActive_asksForNoMoreMoney() {
        makeDue(first);
        jdbc.update("UPDATE contracts SET status = 'SIGNED' WHERE contract_id = ?", id());

        assertThat(requestDue.execute()).isZero();
        assertThat(fundingRequests()).isEmpty();
    }

    @Test
    void theLock_startsTheDelivery_andCountsTheDelayOnTheVietnamCalendar() {
        Instant requestedAt = request(first);
        // Requested Friday 04/12/2026 ICT, locked Tuesday 08/12/2026 ICT: Monday and Tuesday count, 2 days.
        jdbc.update("UPDATE milestone SET funding_requested_at = ? WHERE milestone_id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-12-04T03:00:00Z")), first.milestoneId());

        consumer.onFunded(message(funded(first, "2026-12-08T03:00:00Z")));

        Milestone locked = reload(first);
        assertThat(locked.fundingStatus()).isEqualTo(FundingStatus.LOCKED);
        assertThat(locked.status()).isEqualTo(MilestoneStatus.IN_PROGRESS);
        assertThat(locked.fundingLockedAt()).isEqualTo(Instant.parse("2026-12-08T03:00:00Z"));
        assertThat(locked.fundingDelayBusinessDays()).isEqualTo(2);
        assertThat(requestedAt).isNotNull();
    }

    @Test
    void theSameResultTwice_orAgainUnderANewEventId_changesNothing() {
        request(first);
        EventEnvelope report = funded(first, Instant.now().plusSeconds(5).toString());
        consumer.onFunded(message(report));

        consumer.onFunded(message(report));
        consumer.onFunded(message(funded(first, Instant.now().plusSeconds(3600).toString())));

        assertThat(reload(first).fundingLockedAt()).isEqualTo(
                Instant.parse(report.payload().get("lockedAt").toString()).truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void aFailure_isRecorded_andTheMilestoneKeepsWaiting() {
        request(first);

        consumer.onFundingFailed(message(failed(first, "INSUFFICIENT_FUNDS")));
        consumer.onFundingFailed(message(failed(first, "INSUFFICIENT_FUNDS")));

        Milestone m = reload(first);
        assertThat(m.fundingStatus()).isEqualTo(FundingStatus.FUNDING_FAILED);
        assertThat(m.status()).isEqualTo(MilestoneStatus.CREATED);
        assertThat(m.lastFundingFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void aResultItsMilestoneCannotTake_isRefused_andNotMarkedProcessed() {
        EventEnvelope notRequested = funded(first, Instant.now().toString());
        assertThatThrownBy(() -> consumer.onFunded(message(notRequested)))
                .isInstanceOf(MilestoneFundingRefusedException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class,
                notRequested.eventId().toString())).isZero();

        EventEnvelope unknown = envelope("escrow.milestone_funded", payload(id(), UUID.randomUUID().toString(),
                "lockedAt", Instant.now().toString()));
        assertThatThrownBy(() -> consumer.onFunded(message(unknown)))
                .isInstanceOf(MilestoneFundingRefusedException.class);

        request(first);
        EventEnvelope otherContract = envelope("escrow.milestone_funded", payload(UUID.randomUUID().toString(),
                first.milestoneId(), "lockedAt", Instant.now().plusSeconds(5).toString()));
        assertThatThrownBy(() -> consumer.onFunded(message(otherContract)))
                .isInstanceOf(MilestoneFundingRefusedException.class);
        assertThat(reload(first).fundingStatus()).isEqualTo(FundingStatus.FUNDING_PENDING);
    }

    @Test
    void aPayloadOutsideTheSchema_isRefusedBeforeAnything() {
        Map<String, Object> extra = payload(id(), first.milestoneId(), "lockedAt", Instant.now().toString());
        extra.put("note", "x");
        Map<String, Object> badTime = payload(id(), first.milestoneId(), "lockedAt", "yesterday");
        Map<String, Object> emptyReason = payload(id(), first.milestoneId(), "reason", "");

        assertThatThrownBy(() -> consumer.toFundedCommand(envelope("escrow.milestone_funded", extra)))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.toFundedCommand(envelope("escrow.milestone_funded", badTime)))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.toFailedCommand(envelope("escrow.milestone_funding_failed", emptyReason)))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.toFundedCommand(envelope("escrow.milestone_funding_failed",
                payload(id(), first.milestoneId(), "reason", "x")))).isInstanceOf(InvalidEventPayloadException.class);
    }

    @Test
    void theDatabase_refusesADeliveryStateBeforeTheLock() {
        assertThatThrownBy(() -> jdbc.update("UPDATE milestone SET status = 'IN_PROGRESS' WHERE milestone_id = ?",
                first.milestoneId())).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_milestone_obligation");
        assertThatThrownBy(() -> jdbc.update("UPDATE milestone SET funding_status = 'FUNDING_PENDING' WHERE"
                + " milestone_id = ?", first.milestoneId()))
                .as("a requested batch always has its amount").isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_milestone_batch");
    }

    private Instant request(Milestone milestone) {
        makeDue(milestone);
        requestDue.execute();
        return reload(milestone).fundingRequestedAt();
    }

    private void makeDue(Milestone milestone) {
        jdbc.update("UPDATE milestone SET funding_scheduled_date = ? WHERE milestone_id = ?",
                LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1), milestone.milestoneId());
    }

    private Milestone reload(Milestone milestone) {
        return milestones.findByContractId(id()).stream()
                .filter(m -> m.milestoneId().equals(milestone.milestoneId())).findFirst().orElseThrow();
    }

    private EventEnvelope funded(Milestone milestone, String lockedAt) {
        return envelope("escrow.milestone_funded", payload(id(), milestone.milestoneId(), "lockedAt", lockedAt));
    }

    private EventEnvelope failed(Milestone milestone, String reason) {
        return envelope("escrow.milestone_funding_failed", payload(id(), milestone.milestoneId(), "reason", reason));
    }

    private static Map<String, Object> payload(String contractId, String milestoneId, String field, String value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("milestoneId", milestoneId);
        payload.put(field, value);
        return payload;
    }

    private EventEnvelope envelope(String eventType, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, 1, Instant.now(), "escrow-service", id(), id(),
                UUID.randomUUID().toString(), null, payload);
    }

    private static Message message(EventEnvelope envelope) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(new EventEnvelopeCodec().toJson(envelope).getBytes(), properties);
    }

    private List<JsonNode> fundingRequests() {
        return jdbc.queryForList("SELECT envelope FROM event_outbox WHERE event_type = 'milestone.funding_requested'"
                + " AND aggregate_id = ?", String.class, id()).stream().map(json -> {
                    try {
                        return new ObjectMapper().readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    private String id() {
        return contract.getContractId().value();
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
