package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.infrastructure.config.RabbitMQConfig;
import com.agricontract.escrow.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The service on real MySQL (Flyway migrations) and RabbitMQ: contract.signed in, deposit legs and
 * bank.lock_requested out, checked against the frozen schema. Bank and contract-service are not running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContractSignedFlowIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42").withDatabaseName("escrow_db");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");
    static final String LOCK_COMMANDS = "test.escrow-lock-commands";
    static final String DLQ = RabbitMQConfig.CONTRACT_SIGNED_QUEUE + ".dlq";
    static final ObjectMapper JSON = new ObjectMapper();

    static {
        MYSQL.start(); // No disabled-without-Docker condition: missing integration infrastructure is a failure.
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
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void bindLockCommandQueue() {
        Queue commands = new Queue(LOCK_COMMANDS, false, false, false);
        admin.declareQueue(commands);
        admin.declareBinding(BindingBuilder.bind(commands)
                .to(new TopicExchange("agricontract.escrow")).with("bank.#"));
        admin.purgeQueue(LOCK_COMMANDS, false);
        admin.purgeQueue(DLQ, false);
    }

    @Test
    void redeliveredEventRequestsEachDepositOnce() throws Exception {
        Map<String, Object> event = ContractSignedConsumerTest.validEvent();
        String contractId = (String) payload(event).get("contractId");
        send(event);
        send(event);

        List<JsonNode> commands = receive(2);
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_000)).as("no third command").isNull();
        assertThat(count("SELECT COUNT(*) FROM deposit_leg WHERE contract_id = ?", contractId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id = ?", contractId)).isEqualTo(2);

        List<String> legEventIds = jdbc.queryForList(
                "SELECT request_event_id FROM deposit_leg WHERE contract_id = ?", String.class, contractId);
        for (JsonNode command : commands) {
            FrozenSchemas.assertEvent("bankLockRequested", command);
            assertThat(command.at("/payload/sourceEventId").asText()).isEqualTo(command.get("eventId").asText());
            assertThat(command.get("eventId").asText()).isIn(legEventIds);
            assertThat(command.get("causationId").asText()).isEqualTo(event.get("eventId"));
            assertThat(command.get("correlationId").asText()).isEqualTo(event.get("correlationId"));
        }
        assertThat(commands).extracting(c -> c.at("/payload/entryType").asText())
                .containsExactlyInAnyOrder("LOCK_BUYER_DEPOSIT", "LOCK_SELLER_DEPOSIT");
    }

    @Test
    void contractWithoutASellerDepositRequestsOnlyTheBuyerLeg() throws Exception {
        Map<String, Object> event = ContractSignedConsumerTest.validEvent();
        payload(event).put("sellerDepositAmount", "0.00");
        send(event);

        JsonNode command = receive(1).get(0);
        FrozenSchemas.assertEvent("bankLockRequested", command);
        assertThat(command.at("/payload/entryType").asText()).isEqualTo("LOCK_BUYER_DEPOSIT");
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_000)).isNull();
        assertThat(count("SELECT COUNT(*) FROM deposit_leg WHERE contract_id = ?",
                payload(event).get("contractId"))).isEqualTo(1);
    }

    @Test
    void unreadableEventGoesToTheDlqWithoutWritingAnything() throws Exception {
        Map<String, Object> event = ContractSignedConsumerTest.validEvent();
        payload(event).put("note", "not in the schema");
        send(event);

        assertThat(rabbit.receive(DLQ, 10_000)).isNotNull();
        assertNothingStoredFor(event);
    }

    @Test
    void depositRuleViolationGoesToTheDlqWithoutWritingAnything() throws Exception {
        Map<String, Object> event = ContractSignedConsumerTest.validEvent();
        payload(event).put("buyerDepositAmount", "0.00"); // passes the money pattern, the buyer deposit must be > 0
        send(event);

        assertThat(rabbit.receive(DLQ, 10_000)).isNotNull();
        assertNothingStoredFor(event);
    }

    /** A failure after the dedup row and the first leg are written rolls all of them back, so a retry starts clean. */
    @Test
    void failureRollsBackTheDedupRowLegsAndCommands() throws Exception {
        Map<String, Object> first = ContractSignedConsumerTest.validEvent();
        String contractId = (String) payload(first).get("contractId");
        send(first);
        receive(2);

        // Same contract, new eventId: passes dedup, then hits UNIQUE(contract_id, leg) on the buyer leg.
        Map<String, Object> second = ContractSignedConsumerTest.validEvent();
        second.put("aggregateId", contractId);
        payload(second).put("contractId", contractId);
        send(second);

        assertThat(rabbit.receive(DLQ, 20_000)).as("dead-lettered after the retries").isNotNull();
        assertThat(count("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", second.get("eventId"))).isZero();
        assertThat(count("SELECT COUNT(*) FROM deposit_leg WHERE contract_id = ?", contractId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id = ?", contractId)).isEqualTo(2);
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_000)).isNull();
    }

    private void assertNothingStoredFor(Map<String, Object> event) {
        Object contractId = payload(event).get("contractId");
        assertThat(count("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", event.get("eventId"))).isZero();
        assertThat(count("SELECT COUNT(*) FROM deposit_leg WHERE contract_id = ?", contractId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id = ?", contractId)).isZero();
        assertThat(rabbit.receive(LOCK_COMMANDS, 1_000)).isNull();
    }

    private int count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    private void send(Map<String, Object> event) throws Exception {
        Message message = MessageBuilder.withBody(JSON.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(UUID.randomUUID().toString()).build();
        rabbit.send("agricontract.contracts", "contract.signed", message);
    }

    private List<JsonNode> receive(int count) {
        List<JsonNode> commands = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message message = rabbit.receive(LOCK_COMMANDS, 500);
            if (message != null) commands.add(JSON.readTree(message.getBody()));
            return commands.size() >= count;
        });
        return commands;
    }
}
