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

    @Test
    void cancelBranchQueuesAreDeclaredLikeTheirOwnersDo() {
        assertDurableWithDlx(config.cancelRequestedQueue(), "notification-svc.notification.activation_cancel_requested",
                "notification-svc.contracts.dlx");
        assertDurableWithDlx(config.activationFailedEventQueue(), "product-svc.contract.activation_failed",
                "agricontract.product.dlx");
        assertDurableWithDlx(config.refundRequestedQueue(), "escrow-svc.contract.activation_refund_requested",
                "agricontract.contracts.dlx");
        assertBinding(config.refundRequestedBinding(), "agricontract.contracts",
                "escrow-svc.contract.activation_refund_requested", "contract.activation_refund_requested");
        assertBinding(config.refundRequestedDlqBinding(), "agricontract.contracts.dlx",
                "escrow-svc.contract.activation_refund_requested.dlq", "contract.activation_refund_requested");
    }

    @Test
    void withdrawQueuesAreDeclaredLikeTheirOwnersDo() {
        assertDurableWithDlx(config.withdrawnQueue(), "product-svc.contract.withdrawn", "agricontract.product.dlx");
        assertBinding(config.withdrawnBinding(), "agricontract.contracts", "product-svc.contract.withdrawn",
                "contract.withdrawn");
        assertBinding(config.withdrawnDlqBinding(), "agricontract.product.dlx", "product-svc.contract.withdrawn.dlq",
                "contract.withdrawn");
        assertDurableWithDlx(config.terminatedRequestedQueue(),
                "notification-svc.notification.contract_terminated_requested", "notification-svc.contracts.dlx");
        assertBinding(config.terminatedRequestedBinding(), "agricontract.contracts",
                "notification-svc.notification.contract_terminated_requested",
                "notification.contract_terminated_requested");
        assertBinding(config.terminatedRequestedDlqBinding(), "notification-svc.contracts.dlx",
                "notification-svc.notification.contract_terminated_requested.dlq",
                "notification.contract_terminated_requested");
        // analytics topology.ts: queue analytics-svc.<eventType>, DLX agricontract.analytics.dlx (direct).
        assertDurableWithDlx(config.terminatedQueue(), "analytics-svc.contract.terminated",
                "agricontract.analytics.dlx");
        assertThat(config.analyticsDlx().getType()).isEqualTo("direct");
        assertBinding(config.terminatedBinding(), "agricontract.contracts", "analytics-svc.contract.terminated",
                "contract.terminated");
        assertBinding(config.terminatedDlqBinding(), "agricontract.analytics.dlx",
                "analytics-svc.contract.terminated.dlq", "contract.terminated");
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
