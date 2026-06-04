package com.agricontract.notification.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

// Retry: 3 lần với exponential backoff (1s → 2s → 4s) trước khi mark FAILED
// Idempotency: check eventId trước khi gửi email
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    @RabbitListener(queues = "notification.contract.signed")
    public void onContractSigned(Map<String, Object> event) {
        // TODO: gửi email cho buyer + seller
    }

    @RabbitListener(queues = "notification.contract.cancelled")
    public void onContractCancelled(Map<String, Object> event) {
        // TODO
    }

    @RabbitListener(queues = "notification.goods.delivered")
    public void onGoodsDelivered(Map<String, Object> event) {
        // TODO: notify buyer to confirm
    }

    @RabbitListener(queues = "notification.contract.settled")
    public void onContractSettled(Map<String, Object> event) {
        // TODO: notify both parties
    }

    @RabbitListener(queues = "notification.contract.disputed")
    public void onContractDisputed(Map<String, Object> event) {
        // TODO: notify admin + seller
    }
}
