package com.agricontract.notification.infrastructure.messaging;

import com.agricontract.notification.application.usecase.ProcessNotificationUseCase;
import com.agricontract.notification.infrastructure.messaging.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final ProcessNotificationUseCase processNotificationUseCase;

    @RabbitListener(queues = "notification-svc.contract.signed")
    public void onContractSigned(ContractSignedEvent event) {
        processNotificationUseCase.handleContractSigned(event);
    }

    @RabbitListener(queues = "notification-svc.contract.cancelled")
    public void onContractCancelled(ContractCancelledEvent event) {
        processNotificationUseCase.handleContractCancelled(event);
    }

    @RabbitListener(queues = "notification-svc.contract.delivered")
    public void onContractDelivered(ContractDeliveredEvent event) {
        processNotificationUseCase.handleContractDelivered(event);
    }

    @RabbitListener(queues = "notification-svc.contract.disputed")
    public void onContractDisputed(ContractDisputedEvent event) {
        processNotificationUseCase.handleContractDisputed(event);
    }

    @RabbitListener(queues = "notification-svc.escrow.locked")
    public void onEscrowLocked(EscrowLockedEvent event) {
        processNotificationUseCase.handleEscrowLocked(event);
    }

    @RabbitListener(queues = "notification-svc.escrow.released")
    public void onEscrowReleased(EscrowReleasedEvent event) {
        processNotificationUseCase.handleEscrowReleased(event);
    }

    @RabbitListener(queues = "notification-svc.escrow.penalized")
    public void onEscrowPenalized(EscrowPenalizedEvent event) {
        processNotificationUseCase.handleEscrowPenalized(event);
    }
}
