package com.agricontract.eventing;

import com.agricontract.eventing.inbox.ProcessedEventStore;
import com.agricontract.eventing.outbox.OutboxPoller;
import com.agricontract.eventing.outbox.OutboxProperties;
import com.agricontract.eventing.outbox.OutboxWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Issue #89 acceptance criteria end to end: outbox -> poller -> broker -> consumer (parse, dedupe, handler in one
 * transaction) -> DLQ -> replay. The consumer mirrors what a service listener does; its handler writes a row to
 * test_handled in the same transaction, so a row there means the business effect committed.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(EventingEndToEndIntegrationTest.Config.class)
class EventingEndToEndIntegrationTest {

    private static final String EXCHANGE = "agricontract.test";
    private static final String DLX = "agricontract.test.dlx";
    private static final String QUEUE = "test.e2e.contract.signed";
    private static final String DLQ = QUEUE + ".dlq";
    private static final String ROUTING_KEY = "contract.signed";
    private static final String CONSUMER = "test.e2e.contract-signed";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("db/eventing/V1__create_eventing_tables.sql");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate) {
            return new OutboxWriter(jdbcTemplate, new EventEnvelopeCodec());
        }

        @Bean
        ProcessedEventStore processedEventStore(JdbcTemplate jdbcTemplate) {
            return new ProcessedEventStore(jdbcTemplate);
        }

        @Bean(destroyMethod = "destroy")
        CachingConnectionFactory connectionFactory() {
            CachingConnectionFactory factory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
            factory.setUsername(rabbit.getAdminUsername());
            factory.setPassword(rabbit.getAdminPassword());
            factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
            return factory;
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager txManager;
    @Autowired
    OutboxWriter writer;
    @Autowired
    ProcessedEventStore store;
    @Autowired
    CachingConnectionFactory connectionFactory;

    private final EventEnvelopeCodec codec = new EventEnvelopeCodec();
    private final AtomicBoolean handlerFails = new AtomicBoolean(false);
    private final List<UUID> delivered = new CopyOnWriteArrayList<>();
    private SimpleMessageListenerContainer listener;
    private RabbitAdmin admin;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS test_handled (event_id CHAR(36) NOT NULL)");
        jdbc.update("DELETE FROM test_handled");
        jdbc.update("DELETE FROM processed_event");
        jdbc.update("DELETE FROM event_outbox");

        admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(EXCHANGE);
        DirectExchange dlx = new DirectExchange(DLX);
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        admin.declareExchange(exchange);
        admin.declareExchange(dlx);
        admin.declareQueue(queue);
        admin.declareQueue(dlq);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(dlq).to(dlx).with(ROUTING_KEY));
        admin.purgeQueue(QUEUE);
        admin.purgeQueue(DLQ);

        listener = new SimpleMessageListenerContainer(connectionFactory);
        listener.setQueueNames(QUEUE);
        listener.setDefaultRequeueRejected(false); // a failed delivery dead-letters instead of looping
        listener.setMessageListener(this::consume);
        listener.start();
    }

    @AfterEach
    void tearDown() {
        listener.stop();
    }

    /** What a service listener does: parse strictly, then dedupe and handle in one transaction. */
    private void consume(Message message) {
        EventEnvelope envelope = codec.parse(message.getBody());
        delivered.add(envelope.eventId());
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (!store.markProcessed(CONSUMER, envelope)) {
                return; // duplicate: ack and do nothing
            }
            jdbc.update("INSERT INTO test_handled (event_id) VALUES (?)", envelope.eventId().toString());
            if (handlerFails.get()) {
                throw new IllegalStateException("handler failed");
            }
        });
    }

    private OutboxPoller poller() {
        return new EventingAutoConfiguration.OutboxPollerConfiguration().eventingOutboxPoller(jdbc,
                connectionFactory, txManager, new OutboxProperties(true, EXCHANGE, 50, Duration.ofSeconds(1),
                        Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofMinutes(1)),
                new StandardEnvironment());
    }

    private EventEnvelope produce() {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), ROUTING_KEY, 1,
                Instant.parse("2026-10-24T03:00:00Z"), "contract-service", "contract-1", "corr-1", null,
                new EventSubject(SubjectType.CONTRACT, "contract-1"), Map.of("contractId", "contract-1"));
        new TransactionTemplate(txManager).executeWithoutResult(status -> writer.append(envelope));
        return envelope;
    }

    private int handled(EventEnvelope envelope) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM test_handled WHERE event_id = ?", Integer.class,
                envelope.eventId().toString());
    }

    private int depth(String queue) {
        return admin.getQueueInfo(queue).getMessageCount();
    }

    @Test
    void outboxEventReachesConsumerWithItsOriginalEventIdAndIsHandledOnce() {
        EventEnvelope envelope = produce();

        poller().poll();

        await().atMost(Duration.ofSeconds(10)).until(() -> handled(envelope) == 1);
        assertThat(delivered).containsExactly(envelope.eventId());
    }

    @Test
    void duplicateDelivery_isANoOp() {
        EventEnvelope envelope = produce();
        OutboxPoller poller = poller();
        poller.poll();
        await().atMost(Duration.ofSeconds(10)).until(() -> handled(envelope) == 1);
        // Lost PUBLISHED update: the poller sends the same stored envelope again.
        jdbc.update("UPDATE event_outbox SET status = 'PENDING', published_at = NULL");

        poller.poll();

        await().atMost(Duration.ofSeconds(10)).until(() -> delivered.size() == 2);
        assertThat(delivered).containsOnly(envelope.eventId());
        assertThat(handled(envelope)).isEqualTo(1);
        assertThat(depth(DLQ)).as("a duplicate is acked, never dead-lettered").isZero();
    }

    @Test
    void dlqReplay_preservesEventIdAndIsProcessedExactlyOnce() {
        handlerFails.set(true);
        EventEnvelope envelope = produce();
        poller().poll();
        await().atMost(Duration.ofSeconds(10)).until(() -> depth(DLQ) == 1);
        assertThat(handled(envelope)).as("failed handler rolled back its effect and its dedupe row").isZero();

        // Operator fixes the cause and replays the dead letter unchanged onto the original routing key.
        handlerFails.set(false);
        RabbitTemplate ops = new RabbitTemplate(connectionFactory);
        Message deadLetter = ops.receive(DLQ, 5000);
        assertThat(deadLetter).isNotNull();
        ops.send(EXCHANGE, ROUTING_KEY, deadLetter);

        await().atMost(Duration.ofSeconds(10)).until(() -> handled(envelope) == 1);
        assertThat(codec.parse(deadLetter.getBody()).eventId()).isEqualTo(envelope.eventId());
        assertThat(deadLetter.getMessageProperties().getMessageId()).isEqualTo(envelope.eventId().toString());
        assertThat(delivered).containsOnly(envelope.eventId());

        // Replaying the same dead letter a second time changes nothing.
        ops.send(EXCHANGE, ROUTING_KEY, deadLetter);
        await().atMost(Duration.ofSeconds(10)).until(() -> delivered.size() == 3);
        assertThat(handled(envelope)).isEqualTo(1);
    }

    @Test
    void malformedEnvelope_isDeadLetteredWithoutRunningTheHandler() {
        new RabbitTemplate(connectionFactory).send(EXCHANGE, ROUTING_KEY,
                MessageBuilder.withBody("{\"eventId\":".getBytes(StandardCharsets.UTF_8)).build());

        await().atMost(Duration.ofSeconds(10)).until(() -> depth(DLQ) == 1);
        assertThat(delivered).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isZero();
    }
}
