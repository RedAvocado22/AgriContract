package com.agricontract.bank.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Queues of escrow-service consumers not built yet, for the release and refund results bank-service now writes.
 *
 * <p>The outbox poller publishes with mandatory=true and stops its whole batch on an event no queue receives (D23):
 * an unbound bank.release_completed would block every later result, the bank.lock_completed of other contracts
 * included. Declared exactly as escrow-service declares its bank.lock_* queues (queue {@code escrow-svc.<routingKey>},
 * DLX {@code agricontract.contracts.dlx}), so escrow's own declaration later is a no-op and the results kept here are
 * consumed then. Remove once escrow-service declares them. DlqMonitor watches bank's own DLQs only
 * (RabbitConfiguration.DLQS), not these.
 */
@Configuration
public class ConsumerQueuesDeclaredAhead {

    static final String ESCROW_DLX = "agricontract.contracts.dlx";
    static final List<String> RESULT_KEYS = List.of(
            "bank.release_completed", "bank.release_failed", "bank.refund_completed", "bank.refund_failed");

    @Bean
    Declarables escrowResultQueues() {
        TopicExchange bank = new TopicExchange(RabbitConfiguration.BANK_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(ESCROW_DLX, true, false);
        List<Declarable> declarables = new ArrayList<>(List.of(bank, dlx));
        for (String key : RESULT_KEYS) {
            Queue queue = QueueBuilder.durable("escrow-svc." + key).deadLetterExchange(ESCROW_DLX).build();
            Queue dlq = QueueBuilder.durable("escrow-svc." + key + ".dlq").build();
            Binding binding = BindingBuilder.bind(queue).to(bank).with(key);
            Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(key);
            declarables.addAll(List.of(queue, dlq, binding, dlqBinding));
        }
        return new Declarables(declarables);
    }
}
