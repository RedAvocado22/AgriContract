package com.agricontract.notification.infrastructure.messaging;

import com.agricontract.notification.infrastructure.messaging.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {


    @RabbitListener(queues = "notification-svc.contract.signed")
    public void onContractSigned(ContractSignedEvent event) {
        // TODO: wire to ProcessNotificationUseCase
    }

    @RabbitListener(queues = "notification-svc.contract.cancelled")
    public void onContractCancelled(ContractCancelledEvent event) {
        // TODO
    }

    @RabbitListener(queues = "notification-svc.contract.delivered")
    public void onContractDelivered(ContractDeliveredEvent event) {
        // TODO
    }

    @RabbitListener(queues = "notification-svc.contract.disputed")
    public void onContractDisputed(ContractDisputedEvent event) {
        // TODO
    }

    @RabbitListener(queues = "notification-svc.escrow.locked")
    public void onEscrowLocked(EscrowLockedEvent event) {
        // TODO
    }

    @RabbitListener(queues = "notification-svc.escrow.released")
    public void onEscrowReleased(EscrowReleasedEvent event) {
        // TODO
    }

    @RabbitListener(queues = "notification-svc.escrow.penalized")
    public void onEscrowPenalized(EscrowPenalizedEvent event) {
        // TODO
    }
}
