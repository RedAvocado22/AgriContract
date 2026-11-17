package com.agricontract.inspection.infrastructure.config;

import com.agricontract.inspection.infrastructure.messaging.OutboxAssignmentAlerts;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declare ahead of notification-service, as contract-service's ConsumerQueuesDeclaredAhead does.
 * Commands ride the producer's topic exchange; notification DLX names follow topology.ts's
 * notification-svc.<source>.dlx convention, matching notification-service topology.
 */
@Configuration
public class WaitingNotificationConfiguration {
    public static final String EXCHANGE = "agricontract.inspection";
    public static final String QUEUE = "notification-svc." + OutboxAssignmentAlerts.EVENT_TYPE;
    public static final String DLX = "notification-svc.inspection.dlx";

    @Bean TopicExchange inspectionEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean DirectExchange waitingNotificationDlx() { return new DirectExchange(DLX, true, false); }
    @Bean Queue waitingNotificationQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build(); }
    @Bean Queue waitingNotificationDlq() { return QueueBuilder.durable(QUEUE + ".dlq").build(); }
    @Bean Binding waitingNotificationBinding() {
        return BindingBuilder.bind(waitingNotificationQueue()).to(inspectionEventsExchange()).with(OutboxAssignmentAlerts.EVENT_TYPE);
    }
    @Bean Binding waitingNotificationDlqBinding() {
        return BindingBuilder.bind(waitingNotificationDlq()).to(waitingNotificationDlx()).with(OutboxAssignmentAlerts.EVENT_TYPE);
    }
}
