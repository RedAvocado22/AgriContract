package com.agricontract.reputation.infrastructure;

import com.agricontract.reputation.domain.model.CompletedContractFact;
import com.agricontract.reputation.domain.model.DisputeOutcomeFact;
import com.agricontract.reputation.infrastructure.contract.FrozenSchemas;
import com.agricontract.reputation.infrastructure.messaging.ReputationFactConsumerTest;
import com.agricontract.reputation.infrastructure.persistence.JdbcFactRepository;
import com.agricontract.reputation.infrastructure.messaging.DlqMonitor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReputationRabbitMySqlTest {
    static final String COMPLETED = "reputation-svc.contract.settled";
    static final String DISPUTE = "reputation-svc.milestone.dispute_resolved";
    static final String BROKER_USER = "reputation_test";
    static final String BROKER_PASSWORD = "test-only-broker-password";
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.7-management")
            .withExposedPorts(5672, 15672)
            .withEnv("RABBITMQ_DEFAULT_USER", BROKER_USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", BROKER_PASSWORD)
            .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "-rabbit collect_statistics_interval 100")
            .waitingFor(Wait.forHttp("/api/overview").forPort(15672)
                    .withBasicCredentials(BROKER_USER, BROKER_PASSWORD));
    static { RABBIT.start(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        ReputationDatabaseFixture.properties(r);
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        r.add("spring.rabbitmq.username", () -> BROKER_USER);
        r.add("spring.rabbitmq.password", () -> BROKER_PASSWORD);
        r.add("gateway.internal-secret", () -> "test-only-broker-gateway-secret");
    }
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin brokerAdmin;
    @Autowired JdbcTemplate runtime;
    @Autowired ObjectMapper json;
    @Autowired DlqMonitor monitor;
    @SpyBean(proxyTargetAware = true) JdbcFactRepository facts;
    final HttpClient management = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void resetFixtures() {
        reset(facts);
        for (String queue : List.of(COMPLETED, DISPUTE)) {
            brokerAdmin.purgeQueue(queue, false);
            brokerAdmin.purgeQueue(queue + ".dlq", false);
        }
        ReputationDatabaseFixture.ADMIN.update("DELETE FROM completed_contract_fact");
        ReputationDatabaseFixture.ADMIN.update("DELETE FROM dispute_outcome_fact");
    }
    @Test void canonicalCompletionAndDisputeDeliveryAndReplayAreAcknowledgedOncePerDelivery() throws Exception {
        var completed = ReputationFactConsumerTest.event("contract.settled");
        var dispute = ReputationFactConsumerTest.event("milestone.dispute_resolved");
        FrozenSchemas.assertEvent("contractSettled", completed);
        FrozenSchemas.assertEvent("milestoneDisputeResolved", dispute);
        long completedAcks = acknowledgments(COMPLETED), disputeAcks = acknowledgments(DISPUTE);
        publish(completed); publish(dispute);
        awaitAcknowledgments(COMPLETED, completedAcks + 1);
        awaitAcknowledgments(DISPUTE, disputeAcks + 1);
        publish(completed); publish(dispute);
        awaitAcknowledgments(COMPLETED, completedAcks + 2);
        awaitAcknowledgments(DISPUTE, disputeAcks + 2);
        assertThat(count("completed_contract_fact")).isEqualTo(1);
        assertThat(count("dispute_outcome_fact")).isEqualTo(1);
        verify(facts, times(2)).append(any(CompletedContractFact.class));
        verify(facts, times(2)).append(any(DisputeOutcomeFact.class));
    }
    @Test void brokerDoesNotAcknowledgeUntilTheInsertTransactionHasCommittedAndListenerReturns() throws Exception {
        var entered = new CountDownLatch(1); var permitInsert = new CountDownLatch(1);
        var committed = new CountDownLatch(1); var permitReturn = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown(); requireSignal(permitInsert);
            Object result = call.callRealMethod(); // Real MySQL transaction commits inside append.
            committed.countDown(); requireSignal(permitReturn);
            return result;
        }).when(facts).append(any(CompletedContractFact.class));
        long before = acknowledgments(COMPLETED);
        try {
            publish(ReputationFactConsumerTest.event("contract.settled"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            awaitUnacknowledged(COMPLETED);
            assertThat(count("completed_contract_fact")).isZero();
            assertThat(acknowledgments(COMPLETED)).isEqualTo(before);
            permitInsert.countDown();
            assertThat(committed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(count("completed_contract_fact")).isEqualTo(1); // Separate connection sees committed data.
            assertThat(queueStats(COMPLETED).path("messages_unacknowledged").asInt()).isEqualTo(1);
            assertThat(acknowledgments(COMPLETED)).isEqualTo(before);
        } finally {
            permitInsert.countDown(); permitReturn.countDown();
        }
        awaitAcknowledgments(COMPLETED, before + 1);
    }
    @Test void malformedEventIsRejectedToItsOwnDlqWithoutWriting() throws Exception {
        var invalid = ReputationFactConsumerTest.event("contract.settled");
        ((ObjectNode)invalid.get("payload")).remove("buyerId");
        publish(invalid);
        assertDeadLetter(COMPLETED, invalid);
        verifyNoInteractions(facts);
        assertThat(count("completed_contract_fact")).isZero();
        assertThat(rabbit.receive(DISPUTE + ".dlq")).isNull();
    }
    @Test void conflictingFactIsRejectedWithoutRetryOrMutation() throws Exception {
        var original = ReputationFactConsumerTest.event("contract.settled");
        long before = acknowledgments(COMPLETED);
        publish(original); awaitAcknowledgments(COMPLETED, before + 1);
        var conflicting = original.deepCopy();
        ((ObjectNode)conflicting.get("payload")).put("sellerId", java.util.UUID.randomUUID().toString());
        publish(conflicting);
        assertDeadLetter(COMPLETED, conflicting);
        verify(facts, times(2)).append(any(CompletedContractFact.class)); // One initial insert + one permanent failure.
        assertThat(runtime.queryForObject("SELECT seller_id FROM completed_contract_fact", String.class))
                .isEqualTo(original.path("payload").path("sellerId").asText());
        assertThat(count("completed_contract_fact")).isEqualTo(1);
    }
    @Test void transientDatabaseFailuresRetryAndThenCommitExactlyOneFact() throws Exception {
        var attempts = new AtomicInteger();
        doAnswer(call -> {
            if (attempts.incrementAndGet() < 3) throw new DataAccessResourceFailureException("test-only temporary outage");
            return call.callRealMethod();
        }).when(facts).append(any(CompletedContractFact.class));
        long before = acknowledgments(COMPLETED);
        publish(ReputationFactConsumerTest.event("contract.settled"));
        awaitAcknowledgments(COMPLETED, before + 1);
        assertThat(attempts).hasValue(3);
        assertThat(count("completed_contract_fact")).isEqualTo(1);
        assertThat(rabbit.receive(COMPLETED + ".dlq")).isNull();
    }
    @Test void exhaustedDatabaseRetriesDeadLetterAndRecoveryReplayDoesNotDuplicateFacts() throws Exception {
        doThrow(new DataAccessResourceFailureException("test-only unavailable database"))
                .when(facts).append(any(CompletedContractFact.class));
        var event = ReputationFactConsumerTest.event("contract.settled");
        publish(event);
        Message dead = assertDeadLetter(COMPLETED, event);
        verify(facts, times(3)).append(any(CompletedContractFact.class));
        assertThat(count("completed_contract_fact")).isZero();
        reset(facts); // Database recovery; retain the real repository and transaction behavior.
        long before = acknowledgments(COMPLETED);
        rabbit.send("agricontract.contracts", "contract.settled", dead);
        awaitAcknowledgments(COMPLETED, before + 1);
        rabbit.send("agricontract.contracts", "contract.settled", dead);
        awaitAcknowledgments(COMPLETED, before + 2);
        assertThat(count("completed_contract_fact")).isEqualTo(1);
        assertThat(runtime.queryForObject("SELECT source_event_id FROM completed_contract_fact", String.class))
                .isEqualTo(event.get("eventId").asText());
    }
    @Test void brokerUsesOwnedDlxAndCanonicalBindingsWithoutRoutingOverrides() throws Exception {
        var exchange = managementGet("/api/exchanges/%2F/agricontract.reputation.dlx");
        assertThat(exchange.path("type").asText()).isEqualTo("direct");
        assertThat(exchange.path("durable").asBoolean()).isTrue();
        var bindings = managementGet("/api/exchanges/%2F/agricontract.reputation.dlx/bindings/source");
        assertThat(bindings.size()).isEqualTo(2);
        for (String queue : List.of(COMPLETED, DISPUTE)) {
            var arguments = queueStats(queue).path("arguments");
            assertThat(arguments.path("x-dead-letter-exchange").asText()).isEqualTo("agricontract.reputation.dlx");
            assertThat(arguments.has("x-dead-letter-routing-key")).isFalse();
            assertThat(bindings).anySatisfy(binding -> {
                assertThat(binding.path("destination").asText()).isEqualTo(queue + ".dlq");
                assertThat(binding.path("routing_key").asText()).isEqualTo(queue.substring("reputation-svc.".length()));
            });
        }
    }
    @Test void malformedDisputePreservesItsOwnOriginalKeyAndDlq() throws Exception {
        var invalid = ReputationFactConsumerTest.event("milestone.dispute_resolved");
        ((ObjectNode)invalid.get("payload")).remove("milestoneId");
        publish(invalid);
        assertDeadLetter(DISPUTE, invalid);
        verifyNoInteractions(facts);
        assertThat(count("dispute_outcome_fact")).isZero();
        assertThat(rabbit.receive(COMPLETED + ".dlq")).isNull();
    }
    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void monitorWarnsAboutRealDlqWithoutConsumingIt(org.springframework.boot.test.system.CapturedOutput output) {
        var invalid = ReputationFactConsumerTest.event("contract.settled");
        ((ObjectNode)invalid.get("payload")).remove("buyerId");
        publish(invalid);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(brokerAdmin.getQueueInfo(COMPLETED + ".dlq").getMessageCount()).isEqualTo(1));
        monitor.checkDlqDepth();
        assertThat(output.getOut()).contains(
                "event=reputation_dlq_nonempty queue=reputation-svc.contract.settled.dlq messageCount=1");
        assertThat(brokerAdmin.getQueueInfo(COMPLETED + ".dlq").getMessageCount()).isEqualTo(1);
    }
    void publish(ObjectNode event) {
        rabbit.send("agricontract.contracts", event.path("eventType").asText(), ReputationFactConsumerTest.message(event));
    }
    Message assertDeadLetter(String queue, ObjectNode event) {
        Message dead = rabbit.receive(queue + ".dlq", 15000);
        assertThat(dead).isNotNull();
        assertThat(dead.getBody()).isEqualTo(ReputationFactConsumerTest.message(event).getBody());
        assertThat(dead.getMessageProperties().getReceivedExchange()).isEqualTo("agricontract.reputation.dlx");
        assertThat(dead.getMessageProperties().getReceivedRoutingKey()).isEqualTo(event.path("eventType").asText());
        var deaths = dead.getMessageProperties().getXDeathHeader();
        assertThat(deaths).isNotEmpty();
        assertThat(deaths.getFirst().get("queue").toString()).isEqualTo(queue);
        assertThat(deaths.getFirst().get("reason").toString()).isEqualTo("rejected");
        assertThat(deaths.getFirst().get("routing-keys").toString()).isEqualTo("[" + event.path("eventType").asText() + "]");
        return dead;
    }
    int count(String table) { return runtime.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    void awaitAcknowledgments(String queue, long minimum) {
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            JsonNode stats = queueStats(queue);
            assertThat(stats.path("message_stats").path("ack").asLong()).isGreaterThanOrEqualTo(minimum);
            // Management gauges and cumulative counters are sampled independently;
            // an idle queue can retain an old unacked gauge after its ack counter advances.
            // Use the broker's monotonic ack count and a synchronous AMQP queue declaration.
            assertThat(brokerAdmin.getQueueInfo(queue).getMessageCount()).isZero();
        });
    }
    void awaitUnacknowledged(String queue) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(queueStats(queue).path("messages_unacknowledged").asInt()).isEqualTo(1));
    }
    long acknowledgments(String queue) throws Exception { return queueStats(queue).path("message_stats").path("ack").asLong(); }
    JsonNode queueStats(String queue) throws Exception {
        return managementGet("/api/queues/%2F/" + queue);
    }
    JsonNode managementGet(String path) throws Exception {
        String credentials = Base64.getEncoder().encodeToString((BROKER_USER + ":" + BROKER_PASSWORD).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://" + RABBIT.getHost() + ":" + RABBIT.getMappedPort(15672)
                + path)).timeout(Duration.ofSeconds(5)).header("Authorization", "Basic " + credentials).build();
        var response = management.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
    static void requireSignal(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Test coordination timeout");
    }
}
