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
 * Without this queue the first {@code escrow.deposit_lock_failed} would block every later escrow event, the
 * {@code escrow.deposit_locked} of other contracts included. Declared exactly as contract-service will declare it
 * (#234: queue {@code contract-svc.<routingKey>}, DLX {@code agricontract.escrow.dlx}, as its
 * {@code contract-svc.escrow.deposit_locked}), so its own declaration is a no-op and the messages kept here are
 * consumed then. Remove once contract-service declares it. Plain Queue/Binding beans, not a Declarables: DlqMonitor
 * watches escrow-service's own DLQs only.
 */
@Configuration
public class ConsumerQueuesDeclaredAhead {

    static final String LOCK_FAILED_KEY = "escrow.deposit_lock_failed";
    static final String LOCK_FAILED_QUEUE = "contract-svc." + LOCK_FAILED_KEY;
    static final String CONTRACT_DLX = "agricontract.escrow.dlx";

    private static final TopicExchange ESCROW = new TopicExchange("agricontract.escrow", true, false);

    @Bean
    DirectExchange contractEscrowDlx() {
        return new DirectExchange(CONTRACT_DLX, true, false);
    }

    @Bean
    Queue contractLockFailedQueue() {
        return QueueBuilder.durable(LOCK_FAILED_QUEUE).deadLetterExchange(CONTRACT_DLX).build();
    }

    @Bean
    Queue contractLockFailedDlq() {
        return QueueBuilder.durable(LOCK_FAILED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding contractLockFailedBinding() {
        return BindingBuilder.bind(contractLockFailedQueue()).to(ESCROW).with(LOCK_FAILED_KEY);
    }

    @Bean
    Binding contractLockFailedDlqBinding() {
        return BindingBuilder.bind(contractLockFailedDlq()).to(contractEscrowDlx()).with(LOCK_FAILED_KEY);
    }
}
