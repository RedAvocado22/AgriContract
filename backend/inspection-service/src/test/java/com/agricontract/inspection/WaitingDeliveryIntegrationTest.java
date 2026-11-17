package com.agricontract.inspection;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.infrastructure.config.AssignmentRabbitConfiguration;
import com.agricontract.inspection.infrastructure.config.WaitingNotificationConfiguration;
import com.agricontract.inspection.infrastructure.messaging.OutboxAssignmentAlerts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/** Production consumer, transaction runner, outbox bean and scheduled publisher; no notification-service process. */
@SpringBootTest(properties = {"SERVICE_INTERNAL_SECRET=test-service", "GATEWAY_INTERNAL_SECRET=test-gateway",
        "inspection.assignment.scheduler-enabled=false", "inspection.dlq-monitor.enabled=false",
        "VIETNAM_HOLIDAYS=2026-01-01"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WaitingDeliveryIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42").withDatabaseName("inspection_db");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.rabbitmq.host", RABBIT::getHost);
        p.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        p.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        p.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventEnvelopeCodec codec;
    @MockBean InspectorEligibilityPort inspectors;

    @Test void brokerRequestIsDurableAndDefaultPublisherDeliversWaitingNotificationExactlyOnce() {
        when(inspectors.loadEligibleInspectors()).thenReturn(List.of());
        String contract = UUID.randomUUID().toString(), milestone = UUID.randomUUID().toString();
        var payload = Map.<String, Object>of("contractId", contract, "milestoneId", milestone,
                "reason", "DISPUTE_ROUTED", "reportDueBusinessDays", 2,
                "initiatedByActorId", UUID.randomUUID().toString(), "requestedAt", Instant.now().toString());
        var first = new EventEnvelope(UUID.randomUUID(), AssignmentRabbitConfiguration.KEY, 1, Instant.now(),
                "contract-service", contract, null, null, null, payload);
        var duplicateBusiness = new EventEnvelope(UUID.randomUUID(), AssignmentRabbitConfiguration.KEY, 1, Instant.now(),
                "contract-service", contract, null, null, null, payload);
        for (var request : List.of(first, first, duplicateBusiness)) {
            rabbit.send("agricontract.contracts", AssignmentRabbitConfiguration.KEY,
                    new Message(codec.toJson(request).getBytes(StandardCharsets.UTF_8)));
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE status='PUBLISHED'", Integer.class)).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM level1_5_assignment_waiting", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM level1_5_inspection_assignment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox", Integer.class)).isZero();
        var message = rabbit.receive(WaitingNotificationConfiguration.QUEUE, 5000);
        assertThat(message).isNotNull();
        var command = codec.parse(message.getBody());
        assertThat(command.eventType()).isEqualTo(OutboxAssignmentAlerts.EVENT_TYPE);
        assertThat(command.producer()).isEqualTo("inspection-service");
        assertThat(command.payload()).containsEntry("stage", "WAITING_STARTED").containsEntry("reason", "NO_ELIGIBLE")
                .containsEntry("contractId", contract).containsEntry("milestoneId", milestone);
        assertThat(rabbit.receive(WaitingNotificationConfiguration.QUEUE)).isNull();
    }
}
