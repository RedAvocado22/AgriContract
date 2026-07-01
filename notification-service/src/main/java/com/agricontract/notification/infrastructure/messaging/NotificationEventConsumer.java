package com.agricontract.notification.infrastructure.messaging;

import com.agricontract.notification.application.dto.*;
import com.agricontract.notification.application.exception.InvalidEventPayloadException;
import com.agricontract.notification.application.usecase.ProcessNotificationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final ProcessNotificationUseCase processNotificationUseCase;

    @RabbitListener(queues = "notification-svc.contract.signed")
    public void onContractSigned(Map<String, Object> event) {
        try {
            processNotificationUseCase.handleContractSigned(new ContractSignedCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail")));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.signed payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.contract.cancelled")
    public void onContractCancelled(Map<String, Object> event) {
        try {
            processNotificationUseCase.handleContractCancelled(new ContractCancelledCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail"),
                    (String) event.get("reason")));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.cancelled payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.contract.delivered")
    public void onContractDelivered(Map<String, Object> event) {
        try {
            processNotificationUseCase.handleContractDelivered(new ContractDeliveredCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("sellerEmail")));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.delivered payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.contract.disputed")
    public void onContractDisputed(Map<String, Object> event) {
        try {
            processNotificationUseCase.handleContractDisputed(new ContractDisputedCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail"),
                    (String) event.get("reason")));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.disputed payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.locked")
    public void onEscrowLocked(Map<String, Object> event) {
        try {
            processNotificationUseCase.handleEscrowLocked(new EscrowLockedCommand(
                    (String) event.get("eventId"), (String) event.get("escrowId"),
                    (String) event.get("sellerEmail")));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.locked payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.released")
    public void onEscrowReleased(Map<String, Object> event) {
        try {
            processNotificationUseCase.handleEscrowReleased(new EscrowReleasedCommand(
                    (String) event.get("eventId"), (String) event.get("escrowId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail")));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.released payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.penalized")
    public void onEscrowPenalized(Map<String, Object> event) {
        try {
            String penalizedParty = (String) event.get("penalizedParty");
            String penalizedPartyEmail = "BUYER".equals(penalizedParty)
                    ? (String) event.get("buyerEmail")
                    : (String) event.get("sellerEmail");

            processNotificationUseCase.handleEscrowPenalized(new EscrowPenalizedCommand(
                    (String) event.get("eventId"), (String) event.get("escrowId"), penalizedPartyEmail));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.penalized payload: " + event, e);
        }
    }
}
