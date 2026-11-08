package com.agricontract.escrow.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.agricontract.escrow.application.dto.LockResultCommand;
import com.agricontract.escrow.application.usecase.RecordLockResultUseCase;
import com.agricontract.escrow.infrastructure.config.RabbitMQConfig;
import com.agricontract.escrow.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * #102 on real MySQL and RabbitMQ: contract.signed in, bank.lock_requested out, bank results in, and
 * escrow.deposit_locked out exactly once when every required leg is locked. Retries use a 1s backoff here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DepositLockResultFlowIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42").withDatabaseName("escrow_db");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");
    static final String LOCK_COMMANDS = "test.result-flow.lock-commands";
    static final String DEPOSIT_EVENTS = "test.result-flow.deposit-locked";
    static final String LOCK_FAILED_EVENTS = "test.result-flow.deposit-lock-failed";
    static final String REFUND_COMPLETED_EVENTS = "test.result-flow.activation-refund-completed";
    static final String COMPLETED_DLQ = RabbitMQConfig.LOCK_COMPLETED_QUEUE + ".dlq";
    static final ObjectMapper JSON = new ObjectMapper();

    static {
        MYSQL.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("agricontract.eventing.outbox.poll-interval", () -> "PT0.2S");
        registry.add("escrow.deposit-lock.retry-backoff", () -> "PT1S,PT1S,PT1S");
        registry.add("escrow.deposit-lock.retry-poll-interval", () -> "PT0.2S");
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordLockResultUseCase recordLockResult;
    @Autowired TransactionTemplate transactionTemplate;

    @BeforeEach
    void bindTestQueues() {
        TopicExchange escrow = new TopicExchange("agricontract.escrow");
        Queue commands = new Queue(LOCK_COMMANDS, false, false, false);
        Queue events = new Queue(DEPOSIT_EVENTS, false, false, false);
        admin.declareQueue(commands);
        admin.declareQueue(events);
        admin.declareBinding(BindingBuilder.bind(commands).to(escrow).with("bank.#"));
        admin.declareBinding(BindingBuilder.bind(events).to(escrow).with("escrow.deposit_locked"));
        Queue lockFailed = new Queue(LOCK_FAILED_EVENTS, false, false, false);
        admin.declareQueue(lockFailed);
        admin.declareBinding(BindingBuilder.bind(lockFailed).to(escrow).with("escrow.deposit_lock_failed"));
        admin.purgeQueue(LOCK_FAILED_EVENTS, false);
        Queue refundCompleted = new Queue(REFUND_COMPLETED_EVENTS, false, false, false);
        admin.declareQueue(refundCompleted);
        admin.declareBinding(BindingBuilder.bind(refundCompleted).to(escrow).with("escrow.activation_refund_completed"));
        admin.purgeQueue(REFUND_COMPLETED_EVENTS, false);
        admin.purgeQueue(LOCK_COMMANDS, false);
        admin.purgeQueue(DEPOSIT_EVENTS, false);
        admin.purgeQueue(COMPLETED_DLQ, false);
    }

    @Test
    void bothLegsLocked_publishOneDepositLocked() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");

        result(commands.get("LOCK_BUYER_DEPOSIT"), true);
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 1_500)).as("one leg is not enough").isNull();
        result(commands.get("LOCK_SELLER_DEPOSIT"), true);

        JsonNode event = receiveOne(DEPOSIT_EVENTS);
        FrozenSchemas.assertEvent("escrowDepositLocked", event);
        assertThat(event.at("/payload/buyerDepositState").asText()).isEqualTo("DEPOSIT_LOCKED");
        assertThat(event.at("/payload/sellerDepositState").asText()).isEqualTo("DEPOSIT_LOCKED");
        assertThat(event.get("correlationId").asText())
                .isEqualTo(commands.get("LOCK_SELLER_DEPOSIT").get("correlationId").asText());
        assertThat(status(commands.get("LOCK_BUYER_DEPOSIT"))).isEqualTo("LOCKED");
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 1_500)).isNull();
    }

    @Test
    void duplicateResults_doNotPublishASecondEvent() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        Map<String, Object> buyerLocked = bankResult(commands.get("LOCK_BUYER_DEPOSIT"), true);
        send(buyerLocked);
        send(buyerLocked);                                              // redelivery: same eventId
        result(commands.get("LOCK_BUYER_DEPOSIT"), true);              // bank replay: new eventId, same request
        result(commands.get("LOCK_SELLER_DEPOSIT"), true);
        receiveOne(DEPOSIT_EVENTS);

        result(commands.get("LOCK_SELLER_DEPOSIT"), true);
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 2_000)).isNull();
        assertThat(outboxRows(commands, "escrow.deposit_locked")).isEqualTo(1);
    }

    @Test
    void contractWithoutASellerDeposit_publishesANullSellerState() throws Exception {
        Map<String, JsonNode> commands = sign("0.00");

        result(commands.get("LOCK_BUYER_DEPOSIT"), true);

        JsonNode event = receiveOne(DEPOSIT_EVENTS);
        FrozenSchemas.assertEvent("escrowDepositLocked", event);
        assertThat(event.at("/payload").has("sellerDepositState")).isTrue();
        assertThat(event.at("/payload/sellerDepositState").isNull()).isTrue();
    }

    @Test
    void failedLeg_isResentWithTheSameEventId_onlyThatLeg_thenLocks() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        result(commands.get("LOCK_SELLER_DEPOSIT"), true);

        result(buyer, false);
        await().atMost(Duration.ofSeconds(10)).until(() -> failures(buyer) == 1);

        JsonNode resent = receiveOne(LOCK_COMMANDS);
        assertThat(resent).as("the original command, unchanged").isEqualTo(buyer);
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_500)).as("the locked seller leg is not resent").isNull();

        result(resent, true);
        JsonNode event = receiveOne(DEPOSIT_EVENTS);
        FrozenSchemas.assertEvent("escrowDepositLocked", event);
        assertThat(failures(buyer)).isEqualTo(1);
    }

    @Test
    void lateFailureForALockedLeg_isIgnored() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        result(buyer, true);
        await().atMost(Duration.ofSeconds(10)).until(() -> "LOCKED".equals(status(buyer)));

        result(buyer, false);

        Thread.sleep(1_500);
        assertThat(status(buyer)).isEqualTo("LOCKED");
        assertThat(failures(buyer)).isZero();
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_500)).as("no resend").isNull();
    }

    @Test
    void failureAfterTheLastRetry_staysFailed_withNoFurtherResend() throws Exception {
        Map<String, JsonNode> commands = sign("0.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");

        for (int failure = 1; failure <= 3; failure++) {
            result(buyer, false);
            assertThat(receiveOne(LOCK_COMMANDS)).as("retry " + failure).isEqualTo(buyer);
        }
        result(buyer, false);

        await().atMost(Duration.ofSeconds(10)).until(() -> failures(buyer) == 4);
        assertThat(status(buyer)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT next_attempt_at IS NULL FROM deposit_leg WHERE request_event_id = ?",
                Boolean.class, buyer.get("eventId").asText())).isTrue();
        assertThat(rabbit.receive(LOCK_COMMANDS, 2_500)).as("no fourth retry").isNull();
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 500)).isNull();
    }

    /** #247: buyer locked, seller out of retries → one escrow.deposit_lock_failed; a replay adds nothing. */
    @Test
    void sellerOutOfRetries_whileBuyerLocked_reportsOneLockFailure() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        JsonNode seller = commands.get("LOCK_SELLER_DEPOSIT");
        result(buyer, true);
        exhaust(seller);

        JsonNode event = receiveOne(LOCK_FAILED_EVENTS);
        FrozenSchemas.assertEvent("escrowDepositLockFailed", event);
        assertThat(event.at("/payload/failedLeg").asText()).isEqualTo("SELLER");
        assertThat(event.at("/payload/buyerDepositState").asText()).isEqualTo("DEPOSIT_LOCKED");
        assertThat(event.at("/payload/sellerDepositState").asText()).isEqualTo("LOCK_FAILED");
        assertThat(event.at("/payload/reason").asText()).contains("SELLER: SYSTEM_LOCK_ACTIVE");

        result(seller, false);                                          // bank replay of the last failure
        assertThat(rabbit.receive(LOCK_FAILED_EVENTS, 2_000)).isNull();
        assertThat(outboxRows(commands, "escrow.deposit_lock_failed")).isEqualTo(1);
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 500)).isNull();
    }

    /** #247: one leg out of retries while the other is still retrying → nothing yet; reported once both end. */
    @Test
    void lockFailureWaitsUntilNoLegIsInProgress_thenReportsBoth() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        JsonNode seller = commands.get("LOCK_SELLER_DEPOSIT");
        exhaust(buyer);
        assertThat(rabbit.receive(LOCK_FAILED_EVENTS, 1_500)).as("seller still in progress").isNull();

        exhaust(seller);

        JsonNode event = receiveOne(LOCK_FAILED_EVENTS);
        FrozenSchemas.assertEvent("escrowDepositLockFailed", event);
        assertThat(event.at("/payload/failedLeg").asText()).isEqualTo("BOTH");
        assertThat(outboxRows(commands, "escrow.deposit_lock_failed")).isEqualTo(1);
    }

    /** #247: no seller deposit → the seller state is NOT_REQUIRED (the field is a string, never null). */
    @Test
    void contractWithoutASellerDeposit_reportsTheSellerLegAsNotRequired() throws Exception {
        Map<String, JsonNode> commands = sign("0.00");
        exhaust(commands.get("LOCK_BUYER_DEPOSIT"));

        JsonNode event = receiveOne(LOCK_FAILED_EVENTS);
        FrozenSchemas.assertEvent("escrowDepositLockFailed", event);
        assertThat(event.at("/payload/failedLeg").asText()).isEqualTo("BUYER");
        assertThat(event.at("/payload/sellerDepositState").asText()).isEqualTo("NOT_REQUIRED");
    }

    /** D42: OPERATOR retry restarts only the failed leg with its original request; the lock then succeeds. */
    @Test
    void operatorRetry_resendsTheFailedLegOnly_thenTheContractLocks() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        JsonNode seller = commands.get("LOCK_SELLER_DEPOSIT");
        result(buyer, true);
        exhaust(seller);
        receiveOne(LOCK_FAILED_EVENTS);
        admin.purgeQueue(LOCK_COMMANDS, false);

        retryRequested(buyer);

        assertThat(receiveOne(LOCK_COMMANDS)).as("same request, same eventId").isEqualTo(seller);
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_500)).as("the locked buyer leg is not requested again").isNull();
        assertThat(status(seller)).isEqualTo("REQUESTED");
        assertThat(failures(seller)).isZero();

        result(seller, true);
        FrozenSchemas.assertEvent("escrowDepositLocked", receiveOne(DEPOSIT_EVENTS));
    }

    /** D42: a retry cycle that fails again is reported again, so OPERATOR learns the retry failed too. */
    @Test
    void retryCycleThatFailsAgain_isReportedAgain() throws Exception {
        Map<String, JsonNode> commands = sign("0.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        exhaust(buyer);
        receiveOne(LOCK_FAILED_EVENTS);

        retryRequested(buyer);
        await().atMost(Duration.ofSeconds(10)).until(() -> "REQUESTED".equals(status(buyer)));
        exhaust(buyer);

        receiveOne(LOCK_FAILED_EVENTS);
        assertThat(outboxRows(commands, "escrow.deposit_lock_failed")).isEqualTo(2);
    }

    /** D42: a retry while the legs are still in their cycle (or a redelivered retry) sends nothing. */
    @Test
    void retryWithNothingFailed_sendsNothing() throws Exception {
        Map<String, JsonNode> commands = sign("0.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");

        Map<String, Object> retry = retryEvent(buyer);
        send(retry, "agricontract.contracts");
        send(retry, "agricontract.contracts");

        assertThat(rabbit.receive(LOCK_COMMANDS, 2_000)).isNull();
        assertThat(status(buyer)).isEqualTo("REQUESTED");
    }

    /** #247: buyer locked, seller out of retries, parties cancel → buyer deposit refunded, then completed once. */
    @Test
    void cancelAfterAPartialLock_refundsTheLockedBuyerDeposit_thenReportsCompletionOnce() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        JsonNode seller = commands.get("LOCK_SELLER_DEPOSIT");
        result(buyer, true);
        exhaust(seller);
        receiveOne(LOCK_FAILED_EVENTS);
        admin.purgeQueue(LOCK_COMMANDS, false);

        refundRequested(buyer, List.of("BUYER", "SELLER"), "BOTH_PARTIES");

        JsonNode refund = receiveOne(LOCK_COMMANDS);
        FrozenSchemas.assertEvent("bankRefundRequested", refund);
        assertThat(refund.at("/payload/entryType").asText()).isEqualTo("REFUND_TO_BUYER");
        assertThat(refund.at("/payload/fundType").asText()).isEqualTo("BUYER_DEPOSIT");
        assertThat(refund.at("/payload/amount").asText()).isEqualTo(buyer.at("/payload/amount").asText());
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_500)).as("the seller deposit was never locked").isNull();
        assertThat(rabbit.receive(REFUND_COMPLETED_EVENTS, 500)).as("refund not confirmed yet").isNull();

        Map<String, Object> refunded = returnResult(refund, true);
        send(refunded);
        JsonNode completed = receiveOne(REFUND_COMPLETED_EVENTS);
        FrozenSchemas.assertEvent("escrowActivationRefundCompleted", completed);
        assertThat(completed.at("/payload/returnedLegs")).hasSize(1);
        assertThat(completed.at("/payload/returnedLegs/0/fundType").asText()).isEqualTo("BUYER_DEPOSIT");
        assertThat(completed.at("/payload/returnedLegs/0/ledgerEntryId").asText())
                .isEqualTo(((Map<?, ?>) refunded.get("payload")).get("ledgerEntryId"));

        send(returnResult(refund, true));                              // bank replay
        assertThat(rabbit.receive(REFUND_COMPLETED_EVENTS, 1_500)).isNull();
        assertThat(outboxRows(commands, "escrow.activation_refund_completed")).isEqualTo(1);
        assertThat(status(buyer)).isEqualTo("RETURNED");
        assertThat(status(seller)).isEqualTo("CANCELLED");
    }

    /** #247: nothing locked → completed at once with no returned leg. */
    @Test
    void cancelWithNothingLocked_completesAtOnce() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        exhaust(commands.get("LOCK_BUYER_DEPOSIT"));
        exhaust(commands.get("LOCK_SELLER_DEPOSIT"));
        receiveOne(LOCK_FAILED_EVENTS);
        admin.purgeQueue(LOCK_COMMANDS, false);

        refundRequested(commands.get("LOCK_BUYER_DEPOSIT"), List.of("SELLER"), "ONE_PARTY_AFTER_WINDOW");

        JsonNode completed = receiveOne(REFUND_COMPLETED_EVENTS);
        FrozenSchemas.assertEvent("escrowActivationRefundCompleted", completed);
        assertThat(completed.at("/payload/returnedLegs")).isEmpty();
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_500)).as("no bank command").isNull();
    }

    /** #247: a lock in flight when the parties cancel: completion waits for its result; a late lock is returned. */
    @Test
    void cancelWhileALockIsInFlight_waitsForItsResult_andReturnsALateLock() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        JsonNode seller = commands.get("LOCK_SELLER_DEPOSIT");
        admin.purgeQueue(LOCK_COMMANDS, false);

        refundRequested(buyer, List.of("BUYER", "SELLER"), "BOTH_PARTIES");
        await().atMost(Duration.ofSeconds(10)).until(() -> "CANCEL_PENDING".equals(status(buyer)));
        assertThat(rabbit.receive(REFUND_COMPLETED_EVENTS, 1_000)).as("locks still in flight").isNull();

        result(seller, false);                                          // in-flight lock refused: cancelled
        await().atMost(Duration.ofSeconds(10)).until(() -> "CANCELLED".equals(status(seller)));
        result(buyer, true);                                            // in-flight lock landed: give it back
        JsonNode refund = receiveOne(LOCK_COMMANDS);
        FrozenSchemas.assertEvent("bankRefundRequested", refund);
        assertThat(rabbit.receive(REFUND_COMPLETED_EVENTS, 1_000)).isNull();

        send(returnResult(refund, true));
        assertThat(receiveOne(REFUND_COMPLETED_EVENTS).at("/payload/returnedLegs")).hasSize(1);
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 500)).as("never activated").isNull();
    }

    /** #247: a refused seller release is resent with the same eventId, then confirmed. */
    @Test
    void refusedReturn_isResentWithTheSameEventId() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        JsonNode buyer = commands.get("LOCK_BUYER_DEPOSIT");
        JsonNode seller = commands.get("LOCK_SELLER_DEPOSIT");
        exhaust(buyer);
        result(seller, true);
        receiveOne(LOCK_FAILED_EVENTS);
        admin.purgeQueue(LOCK_COMMANDS, false);

        refundRequested(seller, List.of("BUYER", "SELLER"), "BOTH_PARTIES");
        JsonNode release = receiveOne(LOCK_COMMANDS);
        FrozenSchemas.assertEvent("bankReleaseRequested", release);
        assertThat(release.at("/payload/entryType").asText()).isEqualTo("RELEASE_TO_SELLER");

        send(returnResult(release, false));
        assertThat(receiveOne(LOCK_COMMANDS)).as("same command, same eventId").isEqualTo(release);
        send(returnResult(release, true));

        FrozenSchemas.assertEvent("escrowActivationRefundCompleted", receiveOne(REFUND_COMPLETED_EVENTS));
        assertThat(status(seller)).isEqualTo("RETURNED");
    }

    @Test
    void resultForARequestEscrowNeverSent_goesToTheDlq() throws Exception {
        Map<String, JsonNode> commands = sign("0.00");
        Map<String, Object> stranger = bankResult(commands.get("LOCK_BUYER_DEPOSIT"), true);
        payload(stranger).put("sourceEventId", UUID.randomUUID().toString());
        send(stranger);

        assertThat(rabbit.receive(COMPLETED_DLQ, 20_000)).isNotNull();
        assertThat(status(commands.get("LOCK_BUYER_DEPOSIT"))).isEqualTo("REQUESTED");
    }

    /** Both legs' results at the same instant: the contract row lock makes exactly one of them publish. */
    @Test
    void bothLegsLockedAtOnce_publishExactlyOnce() throws Exception {
        Map<String, JsonNode> commands = sign("20000000.00");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (JsonNode command : commands.values()) {
                runs.add(pool.submit(() -> {
                    start.await();
                    transactionTemplate.executeWithoutResult(s -> recordLockResult.execute(locked(command)));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outboxRows(commands, "escrow.deposit_locked")).isEqualTo(1);
        // Take the published event here, so it does not reach a later test's queue.
        assertThat(receiveOne(DEPOSIT_EVENTS).at("/payload/contractId").asText())
                .isEqualTo(commands.values().iterator().next().at("/payload/contractId").asText());
        assertThat(rabbit.receive(DEPOSIT_EVENTS, 1_500)).isNull();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /** Sends contract.signed and returns the lock commands escrow sent, by entryType. */
    private Map<String, JsonNode> sign(String sellerDeposit) throws Exception {
        Map<String, Object> event = ContractSignedConsumerTest.validEvent();
        payload(event).put("sellerDepositAmount", sellerDeposit);
        Message message = MessageBuilder.withBody(JSON.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbit.send("agricontract.contracts", "contract.signed", message);
        int expected = new BigDecimal(sellerDeposit).signum() > 0 ? 2 : 1;
        Map<String, JsonNode> commands = new LinkedHashMap<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message received = rabbit.receive(LOCK_COMMANDS, 500);
            if (received != null) {
                JsonNode command = JSON.readTree(received.getBody());
                commands.put(command.at("/payload/entryType").asText(), command);
            }
            return commands.size() >= expected;
        });
        return commands;
    }

    /** Fails the leg until no retry is left: three failures each answered by a resend, then the last one. */
    private void exhaust(JsonNode command) throws Exception {
        for (int failure = 1; failure <= 3; failure++) {
            int counted = failure;
            result(command, false);
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> failures(command) == counted && "REQUESTED".equals(status(command)));
        }
        result(command, false);
        await().atMost(Duration.ofSeconds(10)).until(() -> failures(command) == 4);
    }

    private void result(JsonNode command, boolean locked) throws Exception {
        send(bankResult(command, locked));
    }

    /** What bank-service answers to a lock command: new eventId, same sourceEventId, causation = the command. */
    private static Map<String, Object> bankResult(JsonNode command, boolean locked) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String field : List.of("contractId", "userId", "entryType", "fundType", "amount", "sourceEventId")) {
            payload.put(field, command.at("/payload/" + field).asText());
        }
        payload.put("milestoneId", null);
        payload.put("remedyDecisionId", null);
        payload.put("remedyLegId", null);
        if (locked) {
            payload.put("ledgerEntryId", UUID.randomUUID().toString());
        } else {
            payload.put("failureReason", "SYSTEM_LOCK_ACTIVE");
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", locked ? "bank.lock_completed" : "bank.lock_failed");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-11-08T03:00:00Z");
        event.put("producer", "bank-service");
        event.put("aggregateId", payload.get("contractId"));
        event.put("correlationId", command.get("correlationId").asText());
        event.put("causationId", command.get("eventId").asText());
        event.put("payload", payload);
        return event;
    }

    private void send(Map<String, Object> event) throws Exception {
        send(event, "agricontract.bank");
    }

    private void send(Map<String, Object> event, String exchange) throws Exception {
        Message message = MessageBuilder.withBody(JSON.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbit.send(exchange, (String) event.get("eventType"), message);
    }

    /** What contract-service writes when the parties' cancel conditions hold (contractActivationRefundRequested). */
    private void refundRequested(JsonNode anyCommandOfTheContract, List<String> requestedBy, String trigger)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", anyCommandOfTheContract.at("/payload/contractId").asText());
        payload.put("requestedBy", requestedBy);
        payload.put("trigger", trigger);
        payload.put("requestedAt", "2026-11-09T02:00:00Z");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "contract.activation_refund_requested");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-11-09T02:00:00Z");
        event.put("producer", "contract-service");
        event.put("aggregateId", payload.get("contractId"));
        event.put("correlationId", payload.get("contractId"));
        event.put("causationId", null);
        event.put("payload", payload);
        send(event, "agricontract.contracts");
    }

    /** What bank-service answers to a refund/release command (bankRefundCompleted, bankReleaseFailed, ...). */
    private static Map<String, Object> returnResult(JsonNode command, boolean returned) {
        Map<String, Object> result = bankResult(command, returned);
        String operation = command.get("eventType").asText().replace("_requested", "");
        result.put("eventType", operation + (returned ? "_completed" : "_failed"));
        return result;
    }

    private void retryRequested(JsonNode anyCommandOfTheContract) throws Exception {
        send(retryEvent(anyCommandOfTheContract), "agricontract.contracts");
    }

    /** What contract-service writes for OPERATOR RetryDepositLock (contractDepositLockRetryRequested). */
    private static Map<String, Object> retryEvent(JsonNode command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", command.at("/payload/contractId").asText());
        payload.put("operatorUserId", UUID.randomUUID().toString());
        payload.put("requestedAt", "2026-11-09T02:00:00Z");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "contract.deposit_lock_retry_requested");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-11-09T02:00:00Z");
        event.put("producer", "contract-service");
        event.put("aggregateId", payload.get("contractId"));
        event.put("correlationId", payload.get("contractId"));
        event.put("causationId", null);
        event.put("payload", payload);
        return event;
    }

    private static LockResultCommand locked(JsonNode command) {
        return new LockResultCommand(UUID.randomUUID().toString(), command.get("correlationId").asText(),
                command.at("/payload/contractId").asText(), command.at("/payload/sourceEventId").asText(),
                command.at("/payload/userId").asText(), command.at("/payload/entryType").asText(),
                new BigDecimal(command.at("/payload/amount").asText()), true, UUID.randomUUID().toString(), null);
    }

    private JsonNode receiveOne(String queue) throws Exception {
        List<JsonNode> received = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message message = rabbit.receive(queue, 500);
            if (message != null) {
                received.add(JSON.readTree(message.getBody()));
            }
            return !received.isEmpty();
        });
        return received.get(0);
    }

    private String status(JsonNode command) {
        return jdbc.queryForObject("SELECT status FROM deposit_leg WHERE request_event_id = ?", String.class,
                command.get("eventId").asText());
    }

    private int failures(JsonNode command) {
        return jdbc.queryForObject("SELECT failed_attempts FROM deposit_leg WHERE request_event_id = ?",
                Integer.class, command.get("eventId").asText());
    }

    private int outboxRows(Map<String, JsonNode> commands, String eventType) {
        String contractId = commands.values().iterator().next().at("/payload/contractId").asText();
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE event_type = ? AND aggregate_id = ?",
                Integer.class, eventType, contractId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }
}
