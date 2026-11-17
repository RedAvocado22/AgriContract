package com.agricontract.inspection.infrastructure.config;

import com.agricontract.inspection.infrastructure.messaging.OutboxAssignmentAlerts;
import com.rabbitmq.client.Channel;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WaitingNotificationTopologyTest {
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Test
    void bothDeclarationOrdersAreCompatible() throws Exception {
        var factory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        try {
            var admin = new RabbitAdmin(factory);
            var config = new WaitingNotificationConfiguration();
            try (var connection = factory.createConnection(); Channel channel = connection.createChannel(false)) {
                declareInspection(admin, config);
                declareNotification(channel);
                channel.queueDelete(WaitingNotificationConfiguration.QUEUE);
                channel.queueDelete(WaitingNotificationConfiguration.QUEUE + ".dlq");
                channel.exchangeDelete(WaitingNotificationConfiguration.EXCHANGE);
                channel.exchangeDelete(WaitingNotificationConfiguration.DLX);
                declareNotification(channel);
                declareInspection(admin, config);
                channel.queueDeclarePassive(WaitingNotificationConfiguration.QUEUE);
            }
        } finally {
            factory.destroy();
        }
    }

    private static void declareInspection(RabbitAdmin admin, WaitingNotificationConfiguration config) {
        admin.declareExchange(config.inspectionEventsExchange());
        admin.declareExchange(config.waitingNotificationDlx());
        admin.declareQueue(config.waitingNotificationQueue());
        admin.declareQueue(config.waitingNotificationDlq());
        admin.declareBinding(config.waitingNotificationBinding());
        admin.declareBinding(config.waitingNotificationDlqBinding());
    }

    private static void declareNotification(Channel channel) throws Exception {
        String key = OutboxAssignmentAlerts.EVENT_TYPE;
        String queue = "notification-svc." + key;
        channel.exchangeDeclare("agricontract.inspection", "topic", true, false, null);
        channel.exchangeDeclare("notification-svc.inspection.dlx", "direct", true, false, null);
        channel.queueDeclare(queue, true, false, false,
                Map.of("x-dead-letter-exchange", "notification-svc.inspection.dlx"));
        channel.queueDeclare(queue + ".dlq", true, false, false, null);
        channel.queueBind(queue, "agricontract.inspection", key);
        channel.queueBind(queue + ".dlq", "notification-svc.inspection.dlx", key);
    }
}
