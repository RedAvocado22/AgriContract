package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.user.application.dto.InternalUserInfoResponse;
import com.agricontract.user.application.usecase.GetInternalUserInfoUseCase;
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
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * reputation.locked/unlocked from the broker to the lock projection and the notification outbox, on real MySQL and
 * RabbitMQ (verification matrix 18, 21c, 26). The outbox poller stays off, so every command written is still a row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
@Testcontainers(disabledWithoutDocker = true)
class ReputationLockFlowIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final EventEnvelopeCodec CODEC = new EventEnvelopeCodec();
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final String COMMAND = "notification.user_lock_changed_requested";

    // Not @Container: started once and removed by Ryuk at JVM exit, so the cached context keeps its broker.
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @BeforeAll
    static void startContainers() {
        Startables.deepStart(mysql, rabbit).join();
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private GetInternalUserInfoUseCase getInternalUserInfo;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Test
    void lock_updatesProjectionAndWritesOneFrozenCommand() throws Exception {
        String userId = register();
        Instant until = now.plus(30, ChronoUnit.DAYS);
        EventEnvelope locked = ReputationLockEvents.locked(userId, until, 1);

        publish(locked);

        awaitRevision(userId, 1);
        // Eligibility read by product/contract-service now reports the lock.
        InternalUserInfoResponse info = getInternalUserInfo.execute(userId);
        assertThat(info.lockedUntil()).isEqualTo(until);
        JsonNode command = onlyCommand(userId);
        FrozenSchemas.assertNotificationCommand("userLockChangedRequested", command);
        assertThat(command.at("/payload/action").asText()).isEqualTo("LOCKED");
        assertThat(Instant.parse(command.at("/payload/effectiveUntil").asText())).isEqualTo(until);
        assertThat(command.get("causationId").asText()).isEqualTo(locked.eventId().toString());
        assertThat(command.get("correlationId").asText()).isEqualTo(locked.correlationId());
    }

    @Test
    void redeliveredEvent_isAppliedAndNotifiedOnce() throws Exception {
        String userId = register();
        EventEnvelope locked = ReputationLockEvents.locked(userId, now.plus(30, ChronoUnit.DAYS), 1);

        publish(locked);
        publish(locked);
        // A later event on the same queue proves the duplicate before it was already consumed.
        publish(ReputationLockEvents.unlocked(userId, null, 2));

        awaitRevision(userId, 2);
        assertThat(commands(userId)).extracting(c -> c.at("/payload/action").asText())
                .containsExactly("LOCKED", "UNLOCKED");
    }

    // Matrix 18: two overlapping locks, the longer one unlocked early, the shorter one must still hold.
    @Test
    void earlyUnlockOfOneOfTwoLocks_keepsTheRemainingLock() throws Exception {
        String userId = register();
        Instant longer = now.plus(60, ChronoUnit.DAYS);
        Instant shorter = now.plus(30, ChronoUnit.DAYS);

        publish(ReputationLockEvents.locked(userId, longer, 1));
        publish(ReputationLockEvents.locked(userId, shorter, 2));
        awaitRevision(userId, 2);
        // A shorter new lock does not shorten the longer one already in force.
        assertThat(lockedUntil(userId)).isEqualTo(longer);

        publish(ReputationLockEvents.unlocked(userId, shorter, 3));

        awaitRevision(userId, 3);
        assertThat(getInternalUserInfo.execute(userId).lockedUntil()).isEqualTo(shorter);
        List<JsonNode> commands = commands(userId);
        assertThat(commands).hasSize(3);
        assertThat(Instant.parse(commands.get(1).at("/payload/effectiveUntil").asText())).isEqualTo(longer);
        assertThat(Instant.parse(commands.get(2).at("/payload/effectiveUntil").asText())).isEqualTo(shorter);
    }

    // Matrix 21c + 26: the revision is read from the database, so an older event with a new eventId is ignored even
    // when nothing in memory has seen the newer one (what a restart looks like to this consumer).
    @Test
    void olderRevision_isIgnoredUsingThePersistedRevision() throws Exception {
        String userId = register();
        Instant until = now.plus(10, ChronoUnit.DAYS);
        // State left behind by a previous run of the service: revision 5 applied, then the process restarted.
        jdbc.update("UPDATE user_profiles SET locked_until = NULL, last_lock_revision = 5 WHERE user_id = ?", userId);

        publish(ReputationLockEvents.locked(userId, until, 4));
        publish(ReputationLockEvents.locked(userId, until, 5));
        // Fence: revision 6 applying proves 4 and 5 were consumed before it.
        publish(ReputationLockEvents.unlocked(userId, null, 6));

        awaitRevision(userId, 6);
        assertThat(lockedUntil(userId)).isNull();
        assertThat(commands(userId)).extracting(c -> c.at("/payload/action").asText()).containsExactly("UNLOCKED");
    }

    @Test
    void fullUnlock_clearsTheLock() throws Exception {
        String userId = register();
        publish(ReputationLockEvents.locked(userId, now.plus(30, ChronoUnit.DAYS), 1));
        publish(ReputationLockEvents.unlocked(userId, null, 2));

        awaitRevision(userId, 2);
        assertThat(getInternalUserInfo.execute(userId).lockedUntil()).isNull();
        JsonNode unlock = commands(userId).get(1);
        FrozenSchemas.assertNotificationCommand("userLockChangedRequested", unlock);
        assertThat(unlock.at("/payload/effectiveUntil").isNull()).isTrue();
    }

    @Test
    void unknownUser_isAckedWithoutCommand() {
        String userId = UUID.randomUUID().toString();
        EventEnvelope locked = ReputationLockEvents.locked(userId, now.plus(30, ChronoUnit.DAYS), 1);

        publish(locked);

        await().atMost(WAIT).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class,
                locked.eventId().toString()) == 1);
        assertThat(commandCount(userId)).isZero();
        assertThat(rabbitTemplate.receive(RabbitConfiguration.REPUTATION_LOCK_DLQ, 500)).isNull();
    }

    @Test
    void schemaViolation_goesToTheDlqWithoutRetry() {
        String userId = register();
        EventEnvelope source = ReputationLockEvents.locked(userId, now.plus(30, ChronoUnit.DAYS), 1);
        Map<String, Object> payload = new java.util.LinkedHashMap<>(source.payload());
        payload.put("email", "leak@example.com");

        publish(ReputationLockEvents.withPayload(source, payload));

        Message dead = rabbitTemplate.receive(RabbitConfiguration.REPUTATION_LOCK_DLQ, WAIT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo(RabbitConfiguration.REPUTATION_LOCKED_KEY);
        assertThat(revision(userId)).isZero();
        assertThat(commandCount(userId)).isZero();
    }

    @Test
    void schemaViolationOnUnlock_reachesTheDlqWithItsOriginalRoutingKey() {
        String userId = register();
        EventEnvelope source = ReputationLockEvents.unlocked(userId, null, 1);
        Map<String, Object> payload = new java.util.LinkedHashMap<>(source.payload());
        payload.put("email", "leak@example.com");

        publish(ReputationLockEvents.withPayload(source, payload));

        Message dead = rabbitTemplate.receive(RabbitConfiguration.REPUTATION_LOCK_DLQ, WAIT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo(RabbitConfiguration.REPUTATION_UNLOCKED_KEY);
        assertThat(revision(userId)).isZero();
    }

    private String register() {
        String userId = UUID.randomUUID().toString();
        String email = "seller-" + userId.substring(0, 8) + "@example.com";
        UserProfile profile = UserProfile.create(new UserId(userId), "HTX Ca Phe", Role.SELLER,
                new ContactInfo(email, null, null));
        // Only a verified address is notified (design §4.4).
        profile.markEmailVerified(email, Instant.parse("2026-10-01T00:00:00Z"));
        userProfileRepository.save(profile);
        return userId;
    }

    private void publish(EventEnvelope event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(event.eventId().toString());
        rabbitTemplate.send(RabbitConfiguration.REPUTATION_EXCHANGE, event.eventType(),
                MessageBuilder.withBody(CODEC.toJson(event).getBytes(StandardCharsets.UTF_8))
                        .andProperties(properties).build());
    }

    private void awaitRevision(String userId, long revision) {
        await().atMost(WAIT).until(() -> revision(userId) == revision);
    }

    private long revision(String userId) {
        return jdbc.queryForObject("SELECT last_lock_revision FROM user_profiles WHERE user_id = ?",
                Long.class, userId);
    }

    private Instant lockedUntil(String userId) {
        return userProfileRepository.findById(new UserId(userId)).orElseThrow().getLockedUntil();
    }

    private int commandCount(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE event_type = ? AND aggregate_id = ?",
                Integer.class, COMMAND, userId);
    }

    private JsonNode onlyCommand(String userId) throws Exception {
        List<JsonNode> commands = commands(userId);
        assertThat(commands).hasSize(1);
        return commands.get(0);
    }

    private List<JsonNode> commands(String userId) throws Exception {
        List<String> envelopes = jdbc.queryForList(
                "SELECT envelope FROM event_outbox WHERE event_type = ? AND aggregate_id = ? ORDER BY id",
                String.class, COMMAND, userId);
        List<JsonNode> parsed = new java.util.ArrayList<>();
        for (String envelope : envelopes) {
            parsed.add(JSON.readTree(envelope));
        }
        return parsed;
    }
}
