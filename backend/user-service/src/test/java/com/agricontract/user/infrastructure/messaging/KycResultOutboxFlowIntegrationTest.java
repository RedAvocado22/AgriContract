package com.agricontract.user.infrastructure.messaging;

import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.usecase.RejectUserUseCase;
import com.agricontract.user.application.usecase.VerifyUserUseCase;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.agricontract.user.infrastructure.config.RabbitConfiguration;
import com.agricontract.user.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * KYC decision to broker on real MySQL and RabbitMQ: the decision and its command commit together, the poller
 * publishes the stored envelope to agricontract.user and marks it PUBLISHED, and a resend keeps the eventId.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "agricontract.eventing.outbox.enabled=true",
        "agricontract.eventing.outbox.poll-interval=PT0.2S"
})
@Testcontainers(disabledWithoutDocker = true)
class KycResultOutboxFlowIntegrationTest {

    private static final String QUEUE = "test.user-kyc-results";
    private static final String ROUTING_KEY = "notification.user_kyc_result_requested";
    private static final ObjectMapper JSON = new ObjectMapper();

    // Not @Container: started once below and removed by Ryuk at JVM exit. Stopping them after this class would leave
    // the cached context's poller logging connection failures for the rest of the build.
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @BeforeAll
    static void startContainers() {
        Startables.deepStart(mysql, rabbit).join();
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private VerifyUserUseCase verifyUserUseCase;
    @Autowired private RejectUserUseCase rejectUserUseCase;

    @BeforeEach
    void bindQueue() {
        Queue queue = new Queue(QUEUE, false, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitConfiguration.USER_EXCHANGE)).with(ROUTING_KEY));
        amqpAdmin.purgeQueue(QUEUE, false);
    }

    @Test
    void verify_publishesFrozenCommandAndMarksRowPublished() throws Exception {
        String userId = register();

        verifyUserUseCase.execute(new VerifyUserCommand(userId, UUID.randomUUID().toString(), null));

        Message message = receive();
        JsonNode command = JSON.readTree(message.getBody());
        FrozenSchemas.assertNotificationCommand("userKycResultRequested", command);
        String eventId = command.get("eventId").asText();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId);
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(ROUTING_KEY);
        assertThat(command.at("/payload/userId").asText()).isEqualTo(userId);
        assertThat(command.at("/payload/result").asText()).isEqualTo("VERIFIED");
        assertThat(command.get("payload").has("reason")).isFalse();
        UserProfile stored = userProfileRepository.findById(new UserId(userId)).orElseThrow();
        assertThat(Instant.parse(command.at("/payload/decidedAt").asText()))
                .isEqualTo(stored.getVerifiedAt());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(statusOf(eventId)).isEqualTo("PUBLISHED"));
    }

    @Test
    void reject_publishesCommandWithReason() throws Exception {
        String userId = register();

        rejectUserUseCase.execute(new RejectUserCommand(userId, UUID.randomUUID().toString(), "Blurry ID scan"));

        JsonNode command = JSON.readTree(receive().getBody());
        FrozenSchemas.assertNotificationCommand("userKycResultRequested", command);
        assertThat(command.at("/payload/result").asText()).isEqualTo("REJECTED");
        assertThat(command.at("/payload/reason").asText()).isEqualTo("Blurry ID scan");
    }

    @Test
    void resend_reusesTheStoredEventId() throws Exception {
        String userId = register();
        verifyUserUseCase.execute(new VerifyUserCommand(userId, UUID.randomUUID().toString(), null));
        String eventId = JSON.readTree(receive().getBody()).get("eventId").asText();
        await().atMost(Duration.ofSeconds(10)).until(() -> "PUBLISHED".equals(statusOf(eventId)));

        // What a lost confirm looks like to the poller: the row is still PENDING after the broker got it.
        jdbc.update("UPDATE event_outbox SET status = 'PENDING', published_at = NULL WHERE event_id = ?", eventId);

        Message resent = receive();
        assertThat(JSON.readTree(resent.getBody()).get("eventId").asText()).isEqualTo(eventId);
        assertThat(resent.getMessageProperties().getMessageId()).isEqualTo(eventId);
    }

    @Test
    void rolledBackDecision_writesNoCommand() {
        String userId = register();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verifyUserUseCase.execute(new VerifyUserCommand(userId, UUID.randomUUID().toString(), null));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id = ?",
                Integer.class, userId)).isZero();
        assertThat(rabbitTemplate.receive(QUEUE, 1000)).isNull();
    }

    private String register() {
        String userId = UUID.randomUUID().toString();
        userProfileRepository.save(UserProfile.create(new UserId(userId), "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller-" + userId.substring(0, 8) + "@example.com", null, null)));
        return userId;
    }

    private Message receive() {
        Message message = rabbitTemplate.receive(QUEUE, 10_000);
        assertThat(message).as("command on " + QUEUE).isNotNull();
        return message;
    }

    private String statusOf(String eventId) {
        return jdbc.queryForObject("SELECT status FROM event_outbox WHERE event_id = ?", String.class, eventId);
    }
}
