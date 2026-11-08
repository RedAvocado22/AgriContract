package com.agricontract.contract.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues of consumers that are not built yet, for events contract-service already writes to event_outbox.
 *
 * <p>The outbox poller publishes with mandatory=true and stops its whole batch on an event no queue receives (D23).
 * Without these queues, the first {@code notification.contract_anchor_requested} or {@code contract.activated}
 * blocks every later contract event, the next {@code contract.signed} included, so no further contract could
 * lock its deposits. Each queue is declared exactly as its owner will declare it (same name, DLX and DLQ), so the
 * owner's own declaration later is a no-op and the messages kept here are consumed then:
 * <ul>
 *   <li>notification-service (#278): {@code notification-svc.<routingKey>}, DLX {@code notification-svc.contracts.dlx}
 *       (topology.ts);</li>
 *   <li>product-service: {@code product-svc.<routingKey>}, DLX {@code agricontract.product.dlx} (RabbitMQConfig).</li>
 * </ul>
 * Remove a pair once its consumer declares it. Plain Queue/Binding beans, not a Declarables: DlqMonitor watches
 * contract-service's own DLQs only.
 */
@Configuration
public class ConsumerQueuesDeclaredAhead {

    static final String ANCHOR_REQUESTED_KEY = "notification.contract_anchor_requested";
    static final String ANCHOR_REQUESTED_QUEUE = "notification-svc." + ANCHOR_REQUESTED_KEY;
    static final String NOTIFICATION_DLX = "notification-svc.contracts.dlx";

    static final String ACTIVATED_KEY = "contract.activated";
    static final String ACTIVATED_QUEUE = "product-svc." + ACTIVATED_KEY;
    static final String PRODUCT_DLX = "agricontract.product.dlx";

    private static final TopicExchange CONTRACTS = new TopicExchange("agricontract.contracts", true, false);

    @Bean
    DirectExchange notificationContractsDlx() {
        return new DirectExchange(NOTIFICATION_DLX, true, false);
    }

    @Bean
    Queue anchorRequestedQueue() {
        return QueueBuilder.durable(ANCHOR_REQUESTED_QUEUE).deadLetterExchange(NOTIFICATION_DLX).build();
    }

    @Bean
    Queue anchorRequestedDlq() {
        return QueueBuilder.durable(ANCHOR_REQUESTED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding anchorRequestedBinding() {
        return BindingBuilder.bind(anchorRequestedQueue()).to(CONTRACTS).with(ANCHOR_REQUESTED_KEY);
    }

    @Bean
    Binding anchorRequestedDlqBinding() {
        return BindingBuilder.bind(anchorRequestedDlq()).to(notificationContractsDlx()).with(ANCHOR_REQUESTED_KEY);
    }

    @Bean
    DirectExchange productDlx() {
        return new DirectExchange(PRODUCT_DLX, true, false);
    }

    @Bean
    Queue activatedQueue() {
        return QueueBuilder.durable(ACTIVATED_QUEUE).deadLetterExchange(PRODUCT_DLX).build();
    }

    @Bean
    Queue activatedDlq() {
        return QueueBuilder.durable(ACTIVATED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding activatedBinding() {
        return BindingBuilder.bind(activatedQueue()).to(CONTRACTS).with(ACTIVATED_KEY);
    }

    @Bean
    Binding activatedDlqBinding() {
        return BindingBuilder.bind(activatedDlq()).to(productDlx()).with(ACTIVATED_KEY);
    }
}
