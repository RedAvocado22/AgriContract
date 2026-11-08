package com.agricontract.contract.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

/**
 * The queues must match what their owners declare (notification topology.ts, product RabbitMQConfig): a different
 * argument would make the owner's later declaration fail with PRECONDITION_FAILED.
 */
class ConsumerQueuesDeclaredAheadTest {

    private final ConsumerQueuesDeclaredAhead config = new ConsumerQueuesDeclaredAhead();

    @Test
    void anchorRequestedQueueIsDeclaredLikeNotificationServiceDoes() {
        assertDurableWithDlx(config.anchorRequestedQueue(), "notification-svc.notification.contract_anchor_requested",
                "notification-svc.contracts.dlx");
        assertThat(config.anchorRequestedDlq().getName())
                .isEqualTo("notification-svc.notification.contract_anchor_requested.dlq");
        assertBinding(config.anchorRequestedBinding(), "agricontract.contracts",
                "notification-svc.notification.contract_anchor_requested", "notification.contract_anchor_requested");
        assertBinding(config.anchorRequestedDlqBinding(), "notification-svc.contracts.dlx",
                "notification-svc.notification.contract_anchor_requested.dlq", "notification.contract_anchor_requested");
    }

    @Test
    void activatedQueueIsDeclaredLikeProductServiceDoes() {
        assertDurableWithDlx(config.activatedQueue(), "product-svc.contract.activated", "agricontract.product.dlx");
        assertThat(config.activatedDlq().getName()).isEqualTo("product-svc.contract.activated.dlq");
        assertBinding(config.activatedBinding(), "agricontract.contracts", "product-svc.contract.activated",
                "contract.activated");
        assertBinding(config.activatedDlqBinding(), "agricontract.product.dlx", "product-svc.contract.activated.dlq",
                "contract.activated");
    }

    @Test
    void activationFailedQueueIsDeclaredLikeNotificationServiceDoes() {
        assertDurableWithDlx(config.activationFailedRequestedQueue(),
                "notification-svc.notification.contract_activation_failed_requested", "notification-svc.contracts.dlx");
        assertBinding(config.activationFailedRequestedBinding(), "agricontract.contracts",
                "notification-svc.notification.contract_activation_failed_requested",
                "notification.contract_activation_failed_requested");
        assertBinding(config.activationFailedRequestedDlqBinding(), "notification-svc.contracts.dlx",
                "notification-svc.notification.contract_activation_failed_requested.dlq",
                "notification.contract_activation_failed_requested");
    }

    private static void assertDurableWithDlx(Queue queue, String name, String dlx) {
        assertThat(queue.getName()).isEqualTo(name);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.getArguments()).containsExactlyEntriesOf(java.util.Map.of("x-dead-letter-exchange", dlx));
    }

    private static void assertBinding(Binding binding, String exchange, String queue, String routingKey) {
        assertThat(binding.getExchange()).isEqualTo(exchange);
        assertThat(binding.getDestination()).isEqualTo(queue);
        assertThat(binding.getRoutingKey()).isEqualTo(routingKey);
    }
}
