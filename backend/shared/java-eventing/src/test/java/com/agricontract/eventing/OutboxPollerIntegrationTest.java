package com.agricontract.eventing;

import com.agricontract.eventing.outbox.EventOutboxStaleMonitor;
import com.agricontract.eventing.outbox.OutboxPoller;
import com.agricontract.eventing.outbox.OutboxProperties;
import com.agricontract.eventing.outbox.OutboxWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL + RabbitMQ. The poller is built through the auto-configuration's own factory method, so the
 * mandatory flag and confirm checks under test are the ones services get; it is called directly, not scheduled.
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxPollerIntegrationTest {

    private static final String EXCHANGE = "agricontract.test";
    private static final String QUEUE = "test.contract.signed";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("db/eventing/V1__create_eventing_tables.sql");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    private static CachingConnectionFactory connectionFactory;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager txManager;
    private static RabbitTemplate receiver;

    private final EventEnvelopeCodec codec = new EventEnvelopeCodec();

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        txManager = new DataSourceTransactionManager(dataSource);

        connectionFactory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername(rabbit.getAdminUsername());
        connectionFactory.setPassword(rabbit.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(EXCHANGE);
        Queue queue = new Queue(QUEUE);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("contract.signed"));

        receiver = new RabbitTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM event_outbox");
        new RabbitAdmin(connectionFactory).purgeQueue(QUEUE);
    }

    private static OutboxPoller poller(int batchSize) {
        OutboxProperties properties = new OutboxProperties(true, EXCHANGE, batchSize,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofMinutes(1));
        return new EventingAutoConfiguration.OutboxPollerConfiguration()
                .eventingOutboxPoller(jdbc, connectionFactory, txManager, properties, new StandardEnvironment());
    }

    private EventEnvelope append(String eventType) {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), eventType, 1,
                Instant.parse("2026-10-24T03:00:00Z"), "contract-service", "contract-1", "corr-1", null, null,
                Map.of("contractId", "contract-1"));
        // Not a Spring proxy, so MANDATORY is not enforced here; autocommit is enough to seed rows.
        new OutboxWriter(jdbc, codec).append(envelope);
        return envelope;
    }

    private Map<String, Object> row(EventEnvelope envelope) {
        return jdbc.queryForMap("SELECT * FROM event_outbox WHERE event_id = ?", envelope.eventId().toString());
    }

    private List<Message> drain() {
        List<Message> messages = new ArrayList<>();
        Message message;
        while ((message = receiver.receive(QUEUE, 1000)) != null) {
            messages.add(message);
        }
        return messages;
    }

    private EventEnvelope parse(Message message) {
        return codec.parse(message.getBody());
    }

    @Test
    void poll_pendingRow_publishesStoredEnvelopeAndMarksPublished() {
        EventEnvelope envelope = append("contract.signed");

        poller(50).poll();

        List<Message> messages = drain();
        assertThat(messages).hasSize(1);
        Message message = messages.get(0);
        assertThat(parse(message)).isEqualTo(envelope);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(envelope.eventId().toString());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("contract.signed");

        Map<String, Object> row = row(envelope);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("attempts")).isEqualTo(0);
    }

    @Test
    void poll_unroutableEvent_staysPendingWithAttemptAndError() {
        EventEnvelope envelope = append("contract.unbound");

        poller(50).poll();

        Map<String, Object> row = row(envelope);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat((String) row.get("last_error")).startsWith("Unroutable");
        assertThat(row.get("published_at")).isNull();
    }

    @Test
    void poll_mixedBatch_updatesExactlyTheRowThatWasSent() {
        EventEnvelope first = append("contract.signed");
        EventEnvelope unroutable = append("contract.unbound");
        EventEnvelope third = append("contract.signed");

        poller(50).poll();

        assertThat(row(first).get("status")).isEqualTo("PUBLISHED");
        assertThat(row(unroutable).get("status")).isEqualTo("PENDING");
        assertThat(row(unroutable).get("attempts")).isEqualTo(1);
        assertThat(row(third).get("status")).isEqualTo("PUBLISHED");
        assertThat(drain()).extracting(this::parse).containsExactlyInAnyOrder(first, third);
    }

    @Test
    void poll_respectsBatchSize() {
        for (int i = 0; i < 7; i++) {
            append("contract.signed");
        }

        poller(5).poll();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE status = 'PUBLISHED'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE status = 'PENDING'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void poll_resendAfterLostStatusUpdate_carriesTheSameEventId() {
        EventEnvelope envelope = append("contract.signed");
        OutboxPoller poller = poller(50);
        poller.poll();
        // Simulates a crash between broker ack and the PUBLISHED commit: the row is pending again.
        jdbc.update("UPDATE event_outbox SET status = 'PENDING', published_at = NULL");

        poller.poll();

        List<Message> messages = drain();
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(m -> m.getMessageProperties().getMessageId())
                .containsOnly(envelope.eventId().toString());
        assertThat(messages).extracting(this::parse).containsOnly(envelope);
    }

    @Test
    void poll_twoPollersConcurrently_eachEventSentOnce() throws Exception {
        int total = 40;
        for (int i = 0; i < total; i++) {
            append("contract.signed");
        }
        OutboxPoller a = poller(5);
        OutboxPoller b = poller(5);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (OutboxPoller poller : List.of(a, b)) {
                runs.add(pool.submit(() -> {
                    start.await();
                    while (jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE status = 'PENDING'",
                            Integer.class) > 0) {
                        poller.poll();
                    }
                    return null;
                }));
            }
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Message> messages = drain();
        assertThat(messages).hasSize(total);
        assertThat(messages).extracting(m -> m.getMessageProperties().getMessageId()).doesNotHaveDuplicates();
    }

    @Test
    void staleMonitor_countsOnlyPendingRowsOlderThanThreshold() {
        EventEnvelope old = append("contract.unbound");
        EventEnvelope oldPublished = append("contract.signed");
        append("contract.unbound");
        jdbc.update("UPDATE event_outbox SET created_at = NOW(3) - INTERVAL 10 MINUTE WHERE event_id IN (?, ?)",
                old.eventId().toString(), oldPublished.eventId().toString());
        jdbc.update("UPDATE event_outbox SET status = 'PUBLISHED' WHERE event_id = ?", oldPublished.eventId().toString());

        assertThat(new EventOutboxStaleMonitor(jdbc, Duration.ofMinutes(5)).countStale()).isEqualTo(1);
    }
}
