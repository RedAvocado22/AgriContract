package com.agricontract.inspection;

import com.agricontract.eventing.*;
import com.agricontract.inspection.application.port.*;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import com.agricontract.inspection.domain.model.*;
import com.agricontract.inspection.domain.repository.AssignmentLifecycleRepository;
import com.agricontract.inspection.infrastructure.messaging.Level15RequestedConsumer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"SERVICE_INTERNAL_SECRET=test-service", "GATEWAY_INTERNAL_SECRET=test-gateway",
        "inspection.assignment.scheduler-enabled=false",
        "inspection.assignment.holidays=2026-01-01", "management.health.rabbit.enabled=false"})
@Testcontainers
class AssignmentLifecycleIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42").withDatabaseName("inspection_db");
    @Container static final org.testcontainers.containers.RabbitMQContainer RABBIT =
            new org.testcontainers.containers.RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.rabbitmq.host", RABBIT::getHost);
        p.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        p.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        p.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ManageLevel15Assignments lifecycle;
    @Autowired AssignmentLifecycleRepository assignments;
    @Autowired Level15RequestedConsumer consumer;
    @Autowired EventEnvelopeCodec codec;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @MockBean InspectorEligibilityPort inspectors;
    @MockBean RandomGenerator random;
    @MockBean Clock clock;
    @MockBean AssignmentAlerts alerts;
    final String contract = UUID.randomUUID().toString(), milestone = UUID.randomUUID().toString();
    final String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString(), c = UUID.randomUUID().toString();
    static final Instant NOW = Instant.parse("2026-10-23T03:00:00Z");

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM event_outbox");
        jdbc.update("DELETE FROM processed_event");
        jdbc.update("DELETE FROM level1_5_inspection_assignment");
        jdbc.update("DELETE FROM level1_5_assignment_lock");
        when(clock.instant()).thenReturn(NOW);
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of(a,b,c));
    }
    EventEnvelope event() {
        var payload = new LinkedHashMap<String,Object>();
        payload.put("contractId", contract); payload.put("milestoneId", milestone);
        payload.put("reason", "DISPUTE_ROUTED"); payload.put("reportDueBusinessDays", 1);
        payload.put("initiatedByActorId", null); payload.put("requestedAt", NOW.toString());
        return new EventEnvelope(UUID.randomUUID(), "inspection.level1_5_requested", 1, NOW,
                "contract-service", contract, null, null, null, payload);
    }
    void deliver(EventEnvelope e) {
        consumer.accept(new Message(canonicalBytes(e)));
    }
    private byte[] canonicalBytes(EventEnvelope event) {
        return codec.toJson(event).getBytes(StandardCharsets.UTF_8);
    }
    TimedAssignment active() { return assignments.active(contract, milestone).orElseThrow(); }
    int rows() { return jdbc.queryForObject("SELECT COUNT(*) FROM level1_5_inspection_assignment", Integer.class); }

    @Test void canonicalDeliveryDedupAndActiveMilestoneNoOp() {
        when(random.nextInt(3)).thenReturn(2);
        var first = event(); deliver(first); deliver(first); deliver(event());
        assertThat(rows()).isEqualTo(1);
        assertThat(active().inspectorId()).isEqualTo(c);
        assertThat(active().assignedAt()).isEqualTo(NOW);
        verify(inspectors, times(1)).loadEligibleInspectors();
        verify(random).nextInt(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isEqualTo(2);
    }
    @Test void emptyPoolCreatesNoAssignmentAndOnlyOneAlertOnRedelivery() {
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of());
        var event = event(); deliver(event); deliver(event);
        assertThat(rows()).isZero();
        verify(alerts).emptyPool(contract, milestone, event.eventId().toString(), false);
        verifyNoInteractions(random);
    }
    @Test void unavailablePoolRollsBackDedupClaimForRetry() {
        var event = event();
        when(inspectors.loadEligibleInspectors()).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> deliver(event)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isZero();
        doReturn(List.of(a)).when(inspectors).loadEligibleInspectors();
        deliver(event);
        assertThat(rows()).isEqualTo(1);
    }
    @Test void multipleTimeoutsAppendHistoryAndNeverReusePreviousInspectors() {
        deliver(event()); var first = active();
        when(clock.instant()).thenReturn(first.reportDueAt());
        lifecycle.timeout(first.commissionId());
        var second = active();
        assertThat(second.inspectorId()).isEqualTo(b);
        assertThat(second.commissionId()).isNotEqualTo(first.commissionId());
        assertThat(assignments.find(first.commissionId()).orElseThrow().endedAt()).isEqualTo(first.reportDueAt());
        lifecycle.timeout(first.commissionId()); // stale scheduler work is a no-op
        when(clock.instant()).thenReturn(second.reportDueAt().plusSeconds(1));
        lifecycle.timeout(second.commissionId());
        var third = active();
        assertThat(third.inspectorId()).isEqualTo(c);
        when(clock.instant()).thenReturn(third.reportDueAt());
        lifecycle.timeout(third.commissionId());
        lifecycle.timeout(third.commissionId());
        assertThat(rows()).isEqualTo(3);
        assertThat(assignments.active(contract,milestone)).isEmpty();
        verify(alerts).emptyPool(contract,milestone,third.commissionId(),true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM level1_5_inspection_assignment WHERE ended_at IS NOT NULL", Integer.class)).isEqualTo(3);
        verify(random).nextInt(3); verify(random).nextInt(2); verify(random).nextInt(1);
    }
    @Test void beforeDeadlineDoesNotRepick() {
        deliver(event()); lifecycle.timeout(active().commissionId());
        assertThat(rows()).isEqualTo(1);
        verify(inspectors, times(1)).loadEligibleInspectors();
    }

    @Test void scheduledScanFindsOverdueRowsAndCreatesReplacement() {
        deliver(event()); var first = active();
        when(clock.instant()).thenReturn(first.reportDueAt().plusSeconds(1));
        new com.agricontract.inspection.infrastructure.scheduling.AssignmentTimeoutJob(assignments,lifecycle,clock).scan();
        assertThat(rows()).isEqualTo(2);
        assertThat(active().inspectorId()).isEqualTo(b);
    }

    @Test void confirmedAtDeadlineCannotReviveExpiredAuthority() {
        deliver(event()); var old = active();
        when(clock.instant()).thenReturn(old.reportDueAt());
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                lifecycle.closeConfirmed(contract,milestone,old.commissionId(),a,old.reportDueAt())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void structuredOperationalAlertIsEmittedOnlyAfterCommit() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                com.agricontract.inspection.infrastructure.messaging.OperationalAssignmentAlerts.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        var realAlerts = new com.agricontract.inspection.infrastructure.messaging.OperationalAssignmentAlerts();
        var tx = new TransactionTemplate(transactions);
        try {
            tx.executeWithoutResult(status -> {
                realAlerts.emptyPool(contract,milestone,"source",false);
                assertThat(logs.list).isEmpty();
                status.setRollbackOnly();
            });
            assertThat(logs.list).isEmpty();
            tx.executeWithoutResult(status -> realAlerts.emptyPool(contract,milestone,"source",false));
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.getFirst().getFormattedMessage()).contains("audience=OPERATOR", "inspection_assignment_empty_pool");
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
    @Test void confirmationBoundaryRequiresCallerTransactionAndRollsBackTogether() {
        deliver(event()); var assignment = active();
        assertThatThrownBy(() -> lifecycle.closeConfirmed(contract,milestone,assignment.commissionId(),a,NOW))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            lifecycle.closeConfirmed(contract,milestone,assignment.commissionId(),a,NOW);
            throw new IllegalStateException("report confirmation failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(active().endedAt()).isNull();
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                lifecycle.closeConfirmed(contract,milestone,assignment.commissionId(),b,NOW))).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> lifecycle.closeConfirmed(contract,milestone,assignment.commissionId(),a,NOW));
        assertThat(assignments.find(assignment.commissionId()).orElseThrow().endedAt()).isEqualTo(NOW);
        lifecycle.timeout(assignment.commissionId());
        assertThat(rows()).isEqualTo(1);
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                lifecycle.closeConfirmed(contract,milestone,assignment.commissionId(),a,NOW.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void concurrentRequestsForSameMilestoneProduceOneActiveAssignment() throws Exception {
        concurrent(() -> deliver(event()), () -> deliver(event()));
        assertThat(rows()).isEqualTo(1);
        verify(inspectors, times(1)).loadEligibleInspectors();
    }

    @Test void failureAfterInsertRollsBackAssignmentAndDedupTogether() {
        var request = event();
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            deliver(request);
            assertThat(rows()).isEqualTo(1);
            throw new IllegalStateException("caller failed before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(rows()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isZero();
        deliver(request);
        assertThat(rows()).isEqualTo(1);
    }

    @Test void timeoutFailureRollsBackClosureAndCanBeRetried() {
        deliver(event()); var old = active();
        when(clock.instant()).thenReturn(old.reportDueAt());
        when(inspectors.loadEligibleInspectors()).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> lifecycle.timeout(old.commissionId())).isInstanceOf(IllegalStateException.class);
        assertThat(active().commissionId()).isEqualTo(old.commissionId());
        assertThat(active().endedAt()).isNull();
        assertThat(rows()).isEqualTo(1);
        doReturn(List.of(a,b)).when(inspectors).loadEligibleInspectors();
        lifecycle.timeout(old.commissionId());
        assertThat(active().inspectorId()).isEqualTo(b);
        assertThat(rows()).isEqualTo(2);
    }

    @Test void redeliveryAfterExhaustionIsNoOpWhileNewCommandUsesCurrentPool() {
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of(a));
        var original = event(); deliver(original); var old = active();
        when(clock.instant()).thenReturn(old.reportDueAt());
        lifecycle.timeout(old.commissionId());
        deliver(original);
        assertThat(assignments.active(contract,milestone)).isEmpty();
        assertThat(rows()).isEqualTo(1);
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of(b));
        deliver(event());
        assertThat(active().inspectorId()).isEqualTo(b);
        assertThat(rows()).isEqualTo(2);
        assertThat(assignments.find(old.commissionId()).orElseThrow().endedAt()).isEqualTo(old.reportDueAt());
    }

    @Test void legacyRowsAreIgnoredAndQueriesUseSupportingIndexes() {
        for (int i = 0; i < 200; i++) {
            jdbc.update("""
                INSERT INTO level1_5_inspection_assignment
                (commission_id,contract_id,milestone_id,inspector_id,assigned_at,assigned_by_service)
                VALUES (?,?,?,?,?,'contract-service')
                """, UUID.randomUUID().toString(),contract,UUID.randomUUID().toString(),a,java.sql.Timestamp.from(NOW));
        }
        assertThat(assignments.overdue(NOW.plusSeconds(86400*365),100)).isEmpty();
        deliver(event()); var current = active();
        assertThat(assignments.overdue(current.reportDueAt(),100)).extracting(TimedAssignment::commissionId)
                .containsExactly(current.commissionId());
        var timeoutPlan = jdbc.queryForMap("EXPLAIN SELECT * FROM level1_5_inspection_assignment WHERE active_slot=1 AND report_due_at<=? ORDER BY report_due_at,commission_id LIMIT 100",
                java.sql.Timestamp.from(current.reportDueAt()));
        assertThat(timeoutPlan.get("key")).isEqualTo("idx_l15_timeout");
        var historyPlan = jdbc.queryForMap("EXPLAIN SELECT inspector_id FROM level1_5_inspection_assignment WHERE contract_id=? AND milestone_id=? FOR UPDATE",contract,milestone);
        // Both indexes have the exact contract/milestone prefix; the optimizer may prefer
        // the existing unique index for a small history. Do not force a cost-model choice.
        assertThat(historyPlan.get("possible_keys").toString()).contains("idx_l15_previous_inspectors");
        assertThat(historyPlan.get("key")).isIn("idx_l15_previous_inspectors", "uk_l15_active_milestone");
        assertThat(historyPlan.get("type")).isEqualTo("ref");
    }
    @Test void concurrentSameEventIsDeduplicated() throws Exception {
        var same = event();
        concurrent(() -> deliver(same), () -> deliver(same));
        assertThat(rows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentTimeoutsCreateOnlyOneReplacement() throws Exception {
        deliver(event()); var first = active();
        when(clock.instant()).thenReturn(first.reportDueAt());
        concurrent(() -> lifecycle.timeout(first.commissionId()), () -> lifecycle.timeout(first.commissionId()));
        assertThat(rows()).isEqualTo(2);
        assertThat(active().inspectorId()).isEqualTo(b);
    }

    @Test void brokerDeliveryDedupAndMalformedEventDeadLetter() {
        var event = event();
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                new com.agricontract.eventing.outbox.OutboxWriter(jdbc,codec).append(event));
        byte[] body = jdbc.queryForObject("SELECT envelope FROM event_outbox WHERE event_id=?", String.class,
                event.eventId().toString()).getBytes(StandardCharsets.UTF_8);
        rabbit.send("agricontract.contracts", "inspection.level1_5_requested", new Message(body));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> assertThat(rows()).isEqualTo(1));
        rabbit.send("agricontract.contracts", "inspection.level1_5_requested", new Message(body));
        rabbit.send("agricontract.contracts", "inspection.level1_5_requested", new Message("{}".getBytes(StandardCharsets.UTF_8)));
        assertThat(rabbit.receive("inspection-svc.inspection.level1_5_requested.dlq", 15000)).isNotNull();
        assertThat(rows()).isEqualTo(1);
        verify(inspectors, times(1)).loadEligibleInspectors();
    }
    private void concurrent(Runnable one, Runnable two) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = List.of(pool.submit(() -> { await(start); one.run(); }),
                    pool.submit(() -> { await(start); two.run(); }));
            start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
