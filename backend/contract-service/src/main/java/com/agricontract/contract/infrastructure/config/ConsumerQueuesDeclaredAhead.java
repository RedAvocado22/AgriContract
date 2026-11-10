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
 *   <li>notification-service (#278, and #234 for {@code notification.contract_activation_failed_requested}):
 *       {@code notification-svc.<routingKey>}, DLX {@code notification-svc.contracts.dlx} (topology.ts);</li>
 *   <li>product-service: {@code product-svc.<routingKey>}, DLX {@code agricontract.product.dlx} (RabbitMQConfig);</li>
 *   <li>analytics-service (not in compose yet): {@code analytics-svc.<eventType>}, DLX {@code agricontract.analytics.dlx}
 *       (topology.ts), for {@code contract.terminated};</li>
 *   <li>audit-service (#104, not built yet): {@code audit-svc.<routingKey>}, DLX {@code agricontract.audit.dlx}, for
 *       {@code contract.signed} and {@code contract.terminated} (#399), so contracts signed or ended before audit
 *       exists still reach the audit chain;</li>
 *   <li>escrow-service, until #385 merges: {@code escrow-svc.contract.activation_refund_requested}, DLX
 *       {@code agricontract.contracts.dlx} (its RabbitMQConfig).</li>
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

    static final String ACTIVATION_FAILED_KEY = "notification.contract_activation_failed_requested";
    static final String ACTIVATION_FAILED_QUEUE = "notification-svc." + ACTIVATION_FAILED_KEY;

    static final String CANCEL_REQUESTED_KEY = "notification.activation_cancel_requested";
    static final String CANCEL_REQUESTED_QUEUE = "notification-svc." + CANCEL_REQUESTED_KEY;
    static final String ACTIVATION_FAILED_EVENT_KEY = "contract.activation_failed";
    static final String ACTIVATION_FAILED_EVENT_QUEUE = "product-svc." + ACTIVATION_FAILED_EVENT_KEY;
    static final String REFUND_REQUESTED_KEY = "contract.activation_refund_requested";
    static final String REFUND_REQUESTED_QUEUE = "escrow-svc." + REFUND_REQUESTED_KEY;
    static final String ESCROW_DLX = "agricontract.contracts.dlx";

    static final String WITHDRAWN_KEY = "contract.withdrawn";
    static final String WITHDRAWN_QUEUE = "product-svc." + WITHDRAWN_KEY;
    static final String TERMINATED_REQUESTED_KEY = "notification.contract_terminated_requested";
    static final String TERMINATED_REQUESTED_QUEUE = "notification-svc." + TERMINATED_REQUESTED_KEY;
    static final String TERMINATED_KEY = "contract.terminated";
    static final String TERMINATED_QUEUE = "analytics-svc." + TERMINATED_KEY;
    static final String ANALYTICS_DLX = "agricontract.analytics.dlx";
    static final String SIGNED_KEY = "contract.signed";
    static final String AUDIT_SIGNED_QUEUE = "audit-svc." + SIGNED_KEY;
    static final String AUDIT_TERMINATED_QUEUE = "audit-svc." + TERMINATED_KEY;
    static final String AUDIT_DLX = "agricontract.audit.dlx";

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

    @Bean
    Queue activationFailedRequestedQueue() {
        return QueueBuilder.durable(ACTIVATION_FAILED_QUEUE).deadLetterExchange(NOTIFICATION_DLX).build();
    }

    @Bean
    Queue activationFailedRequestedDlq() {
        return QueueBuilder.durable(ACTIVATION_FAILED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding activationFailedRequestedBinding() {
        return BindingBuilder.bind(activationFailedRequestedQueue()).to(CONTRACTS).with(ACTIVATION_FAILED_KEY);
    }

    @Bean
    Binding activationFailedRequestedDlqBinding() {
        return BindingBuilder.bind(activationFailedRequestedDlq()).to(notificationContractsDlx())
                .with(ACTIVATION_FAILED_KEY);
    }

    @Bean
    Queue cancelRequestedQueue() {
        return QueueBuilder.durable(CANCEL_REQUESTED_QUEUE).deadLetterExchange(NOTIFICATION_DLX).build();
    }

    @Bean
    Queue cancelRequestedDlq() {
        return QueueBuilder.durable(CANCEL_REQUESTED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding cancelRequestedBinding() {
        return BindingBuilder.bind(cancelRequestedQueue()).to(CONTRACTS).with(CANCEL_REQUESTED_KEY);
    }

    @Bean
    Binding cancelRequestedDlqBinding() {
        return BindingBuilder.bind(cancelRequestedDlq()).to(notificationContractsDlx()).with(CANCEL_REQUESTED_KEY);
    }

    @Bean
    Queue activationFailedEventQueue() {
        return QueueBuilder.durable(ACTIVATION_FAILED_EVENT_QUEUE).deadLetterExchange(PRODUCT_DLX).build();
    }

    @Bean
    Queue activationFailedEventDlq() {
        return QueueBuilder.durable(ACTIVATION_FAILED_EVENT_QUEUE + ".dlq").build();
    }

    @Bean
    Binding activationFailedEventBinding() {
        return BindingBuilder.bind(activationFailedEventQueue()).to(CONTRACTS).with(ACTIVATION_FAILED_EVENT_KEY);
    }

    @Bean
    Binding activationFailedEventDlqBinding() {
        return BindingBuilder.bind(activationFailedEventDlq()).to(productDlx()).with(ACTIVATION_FAILED_EVENT_KEY);
    }

    @Bean
    DirectExchange escrowContractsDlx() {
        return new DirectExchange(ESCROW_DLX, true, false);
    }

    @Bean
    Queue refundRequestedQueue() {
        return QueueBuilder.durable(REFUND_REQUESTED_QUEUE).deadLetterExchange(ESCROW_DLX).build();
    }

    @Bean
    Queue refundRequestedDlq() {
        return QueueBuilder.durable(REFUND_REQUESTED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding refundRequestedBinding() {
        return BindingBuilder.bind(refundRequestedQueue()).to(CONTRACTS).with(REFUND_REQUESTED_KEY);
    }

    @Bean
    Binding refundRequestedDlqBinding() {
        return BindingBuilder.bind(refundRequestedDlq()).to(escrowContractsDlx()).with(REFUND_REQUESTED_KEY);
    }

    // #233 withdraw: product-service (#126) releases the reservation, notification-service (#278) emails the other
    // party, analytics-service records the termination.

    @Bean
    Queue withdrawnQueue() {
        return QueueBuilder.durable(WITHDRAWN_QUEUE).deadLetterExchange(PRODUCT_DLX).build();
    }

    @Bean
    Queue withdrawnDlq() {
        return QueueBuilder.durable(WITHDRAWN_QUEUE + ".dlq").build();
    }

    @Bean
    Binding withdrawnBinding() {
        return BindingBuilder.bind(withdrawnQueue()).to(CONTRACTS).with(WITHDRAWN_KEY);
    }

    @Bean
    Binding withdrawnDlqBinding() {
        return BindingBuilder.bind(withdrawnDlq()).to(productDlx()).with(WITHDRAWN_KEY);
    }

    @Bean
    Queue terminatedRequestedQueue() {
        return QueueBuilder.durable(TERMINATED_REQUESTED_QUEUE).deadLetterExchange(NOTIFICATION_DLX).build();
    }

    @Bean
    Queue terminatedRequestedDlq() {
        return QueueBuilder.durable(TERMINATED_REQUESTED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding terminatedRequestedBinding() {
        return BindingBuilder.bind(terminatedRequestedQueue()).to(CONTRACTS).with(TERMINATED_REQUESTED_KEY);
    }

    @Bean
    Binding terminatedRequestedDlqBinding() {
        return BindingBuilder.bind(terminatedRequestedDlq()).to(notificationContractsDlx())
                .with(TERMINATED_REQUESTED_KEY);
    }

    @Bean
    DirectExchange analyticsDlx() {
        return new DirectExchange(ANALYTICS_DLX, true, false);
    }

    @Bean
    Queue terminatedQueue() {
        return QueueBuilder.durable(TERMINATED_QUEUE).deadLetterExchange(ANALYTICS_DLX).build();
    }

    @Bean
    Queue terminatedDlq() {
        return QueueBuilder.durable(TERMINATED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding terminatedBinding() {
        return BindingBuilder.bind(terminatedQueue()).to(CONTRACTS).with(TERMINATED_KEY);
    }

    @Bean
    Binding terminatedDlqBinding() {
        return BindingBuilder.bind(terminatedDlq()).to(analyticsDlx()).with(TERMINATED_KEY);
    }

    // #399: audit-service (#104) is the only writer of the audit chain and is not built yet. Without these queues
    // every contract.signed / contract.terminated published before it exists would never reach the chain.

    @Bean
    DirectExchange auditDlx() {
        return new DirectExchange(AUDIT_DLX, true, false);
    }

    @Bean
    Queue auditSignedQueue() {
        return QueueBuilder.durable(AUDIT_SIGNED_QUEUE).deadLetterExchange(AUDIT_DLX).build();
    }

    @Bean
    Queue auditSignedDlq() {
        return QueueBuilder.durable(AUDIT_SIGNED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding auditSignedBinding() {
        return BindingBuilder.bind(auditSignedQueue()).to(CONTRACTS).with(SIGNED_KEY);
    }

    @Bean
    Binding auditSignedDlqBinding() {
        return BindingBuilder.bind(auditSignedDlq()).to(auditDlx()).with(SIGNED_KEY);
    }

    @Bean
    Queue auditTerminatedQueue() {
        return QueueBuilder.durable(AUDIT_TERMINATED_QUEUE).deadLetterExchange(AUDIT_DLX).build();
    }

    @Bean
    Queue auditTerminatedDlq() {
        return QueueBuilder.durable(AUDIT_TERMINATED_QUEUE + ".dlq").build();
    }

    @Bean
    Binding auditTerminatedBinding() {
        return BindingBuilder.bind(auditTerminatedQueue()).to(CONTRACTS).with(TERMINATED_KEY);
    }

    @Bean
    Binding auditTerminatedDlqBinding() {
        return BindingBuilder.bind(auditTerminatedDlq()).to(auditDlx()).with(TERMINATED_KEY);
    }
}
