package com.agricontract.escrow.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue of a consumer not built yet, for an event escrow-service already writes to event_outbox.
 *
 * <p>The outbox poller publishes with mandatory=true and stops its whole batch on an event no queue receives (D23).
 * Without this queue the first {@code escrow.activation_refund_completed} would block every later escrow event.
 * Declared exactly as contract-service declares its escrow queues (queue {@code contract-svc.<routingKey>}, DLX
 * {@code agricontract.escrow.dlx}), so its own declaration (#234) is a no-op and the event kept here is consumed then.
 * Remove once contract-service declares it. Plain Queue/Binding beans, not a Declarables: DlqMonitor watches
 * escrow-service's own DLQs only.
 */
@Configuration
public class ConsumerQueuesDeclaredAhead {

    static final String REFUND_COMPLETED_KEY = "escrow.activation_refund_completed";
    static final String REFUND_COMPLETED_QUEUE = "contract-svc." + REFUND_COMPLETED_KEY;
    static final String CONTRACT_DLX = "agricontract.escrow.dlx";

    private static final TopicExchange ESCROW = new TopicExchange("agricontract.escrow", true, false);

    @Bean
    DirectExchange contractEscrowDlx() {
        return new DirectExchange(CONTRACT_DLX, true, false);
    }

    @Bean
    Queue contractRefundCompletedQueue() {
        return QueueBuilder.durable(REFUND_COMPLETED_QUEUE).deadLetterExchange(CONTRACT_DLX).build();
    }

    @Bean
    Queue contractRefundCompletedDlq() {
        return QueueBuilder.durable(REFUND_COMPLETED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding contractRefundCompletedBinding() {
        return BindingBuilder.bind(contractRefundCompletedQueue()).to(ESCROW).with(REFUND_COMPLETED_KEY);
    }

    @Bean
    Binding contractRefundCompletedDlqBinding() {
        return BindingBuilder.bind(contractRefundCompletedDlq()).to(contractEscrowDlx()).with(REFUND_COMPLETED_KEY);
    }
}
