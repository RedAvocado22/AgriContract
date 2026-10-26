package com.agricontract.bank.infrastructure.messaging;

import com.agricontract.bank.infrastructure.config.RabbitConfiguration;
import com.agricontract.bank.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole service on real MySQL (production migrations and grants) and RabbitMQ: verification matrix 1 and 6,
 * results checked against the frozen bank.lock_* schemas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LockRequestFlowIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42")
            .withDatabaseName("bank_db").withUsername("bank_migration")
            .withPassword("test-only-migration-password").withEnv("MYSQL_ROOT_HOST", "%");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");
    static final String RESULTS = "test.bank-lock-results";
    static final ObjectMapper JSON = new ObjectMapper();

    static {
        MYSQL.start();
        RABBIT.start();
        try {
            // Production order: Flyway as the migration account, then ops/grants.sql for the runtime account.
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            JdbcTemplate admin = new JdbcTemplate(
                    new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
            admin.execute("CREATE USER 'bank_runtime'@'%' IDENTIFIED BY 'test-only-runtime-password'");
            String sql = Files.readString(Path.of("ops/grants.sql")).replaceAll("(?m)--[^\\r\\n]*", "");
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) admin.execute(statement);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "bank_runtime");
        registry.add("spring.datasource.password", () -> "test-only-runtime-password");
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("agricontract.eventing.outbox.poll-interval", () -> "PT0.2S");
        registry.add("bank.dlq-monitor.enabled", () -> "false");
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void bindResultQueue() {
        Queue results = new Queue(RESULTS, false, false, false);
        admin.declareQueue(results);
        admin.declareBinding(BindingBuilder.bind(results)
                .to(new TopicExchange(RabbitConfiguration.BANK_EXCHANGE)).with("bank.#"));
        admin.purgeQueue(RESULTS, false);
        admin.purgeQueue(RabbitConfiguration.LOCK_REQUEST_DLQ, false);
    }

    @Test
    void repeatedRequestLocksOnceAndConfirmsEveryTime() throws Exception {
        Map<String, Object> request = LockRequestConsumerTest.validRequest();
        String requestId = (String) request.get("eventId");
        send(request);
        send(request);

        List<JsonNode> results = receive(2);
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry WHERE source_event_id = ?",
                Integer.class, requestId);
        String entryId = jdbc.queryForObject("SELECT entry_id FROM ledger_entry WHERE source_event_id = ?",
                String.class, requestId);

        assertThat(rows).isEqualTo(1);
        assertThat(results).extracting(r -> r.get("eventId").asText()).doesNotHaveDuplicates()
                .doesNotContain(requestId);
        for (JsonNode result : results) {
            FrozenSchemas.assertEvent("bankLockCompleted", result);
            assertThat(result.get("causationId").asText()).isEqualTo(requestId);
            assertThat(result.get("correlationId").asText()).isEqualTo(request.get("correlationId"));
            assertThat(result.at("/payload/sourceEventId").asText()).isEqualTo(requestId);
            assertThat(result.at("/payload/ledgerEntryId").asText()).isEqualTo(entryId);
        }
    }

    @Test
    void ledgerRuleViolationIsAnsweredWithLockFailed() throws Exception {
        Map<String, Object> request = LockRequestConsumerTest.validRequest();
        @SuppressWarnings("unchecked") Map<String, Object> payload = (Map<String, Object>) request.get("payload");
        payload.put("entryType", "LOCK_MILESTONE");
        payload.put("fundType", "MILESTONE_PAYMENT"); // a milestone lock with no milestoneId
        send(request);

        JsonNode result = receive(1).get(0);
        FrozenSchemas.assertEvent("bankLockFailed", result);
        assertThat(result.get("eventType").asText()).isEqualTo("bank.lock_failed");
        assertThat(result.at("/payload/failureReason").asText()).contains("milestoneId");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry WHERE source_event_id = ?",
                Integer.class, request.get("eventId"))).isZero();
    }

    @Test
    void unreadableRequestGoesToTheDlqWithoutAnyResult() throws Exception {
        Map<String, Object> request = LockRequestConsumerTest.validRequest();
        request.remove("correlationId");
        send(request);

        Message dead = rabbit.receive(RabbitConfiguration.LOCK_REQUEST_DLQ, 10_000);
        assertThat(dead).isNotNull();
        assertThat(rabbit.receive(RESULTS, 1_000)).isNull();
    }

    private void send(Map<String, Object> event) throws Exception {
        Message message = MessageBuilder.withBody(JSON.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbit.send(RabbitConfiguration.ESCROW_EXCHANGE, RabbitConfiguration.LOCK_REQUEST_KEY, message);
    }

    private List<JsonNode> receive(int count) {
        List<JsonNode> results = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message message = rabbit.receive(RESULTS, 500);
            if (message != null) results.add(JSON.readTree(message.getBody()));
            return results.size() >= count;
        });
        return results;
    }
}
