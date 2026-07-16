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
        processNotificationUseCase.handleContractSigned(parseContractSignedEvent(event));
    }

    private ContractSignedCommand parseContractSignedEvent(Map<String, Object> event) {
        try {
            return new ContractSignedCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.signed payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.contract.cancelled")
    public void onContractCancelled(Map<String, Object> event) {
        processNotificationUseCase.handleContractCancelled(parseContractCancelledEvent(event));
    }

    private ContractCancelledCommand parseContractCancelledEvent(Map<String, Object> event) {
        try {
            return new ContractCancelledCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail"),
                    (String) event.get("reason"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.cancelled payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.contract.delivered")
    public void onContractDelivered(Map<String, Object> event) {
        processNotificationUseCase.handleContractDelivered(parseContractDeliveredEvent(event));
    }

    private ContractDeliveredCommand parseContractDeliveredEvent(Map<String, Object> event) {
        try {
            return new ContractDeliveredCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("sellerEmail"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.delivered payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.contract.disputed")
    public void onContractDisputed(Map<String, Object> event) {
        processNotificationUseCase.handleContractDisputed(parseContractDisputedEvent(event));
    }

    private ContractDisputedCommand parseContractDisputedEvent(Map<String, Object> event) {
        try {
            return new ContractDisputedCommand(
                    (String) event.get("eventId"), (String) event.get("contractId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail"),
                    (String) event.get("reason"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed contract.disputed payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.locked")
    public void onEscrowLocked(Map<String, Object> event) {
        processNotificationUseCase.handleEscrowLocked(parseEscrowLockedEvent(event));
    }

    private EscrowLockedCommand parseEscrowLockedEvent(Map<String, Object> event) {
        try {
            return new EscrowLockedCommand(
                    (String) event.get("eventId"), (String) event.get("escrowId"),
                    (String) event.get("sellerEmail"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.locked payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.released")
    public void onEscrowReleased(Map<String, Object> event) {
        processNotificationUseCase.handleEscrowReleased(parseEscrowReleasedEvent(event));
    }

    private EscrowReleasedCommand parseEscrowReleasedEvent(Map<String, Object> event) {
        try {
            return new EscrowReleasedCommand(
                    (String) event.get("eventId"), (String) event.get("escrowId"),
                    (String) event.get("buyerEmail"), (String) event.get("sellerEmail"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.released payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.penalized")
    public void onEscrowPenalized(Map<String, Object> event) {
        processNotificationUseCase.handleEscrowPenalized(parseEscrowPenalizedEvent(event));
    }

    private EscrowPenalizedCommand parseEscrowPenalizedEvent(Map<String, Object> event) {
        try {
            String penalizedParty = (String) event.get("penalizedParty");
            String penalizedPartyEmail = "BUYER".equals(penalizedParty)
                    ? (String) event.get("buyerEmail")
                    : (String) event.get("sellerEmail");

            return new EscrowPenalizedCommand(
                    (String) event.get("eventId"), (String) event.get("escrowId"), penalizedPartyEmail);
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.penalized payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.escrow.arbitrated")
    public void onEscrowArbitrated(Map<String, Object> event) {
        processNotificationUseCase.handleEscrowArbitrated(parseEscrowArbitratedEvent(event));
    }

    private EscrowArbitratedCommand parseEscrowArbitratedEvent(Map<String, Object> event) {
        try {
            return new EscrowArbitratedCommand(
                    (String) event.get("eventId"),
                    (String) event.get("escrowId"),
                    (String) event.get("contractId"),
                    (String) event.get("buyerEmail"),
                    (String) event.get("sellerEmail"),
                    (String) event.get("justification"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed escrow.arbitrated payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.category.approved")
    public void onCategoryApproved(Map<String, Object> event) {
        processNotificationUseCase.handleCategoryApproved(parseCategoryApprovedEvent(event));
    }

    private CategoryApprovedCommand parseCategoryApprovedEvent(Map<String, Object> event) {
        try {
            return new CategoryApprovedCommand(
                    (String) event.get("eventId"), (String) event.get("categoryId"),
                    (String) event.get("name"), (String) event.get("proposedByEmail"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed category.approved payload: " + event, e);
        }
    }

    @RabbitListener(queues = "notification-svc.category.rejected")
    public void onCategoryRejected(Map<String, Object> event) {
        processNotificationUseCase.handleCategoryRejected(parseCategoryRejectedEvent(event));
    }

    private CategoryRejectedCommand parseCategoryRejectedEvent(Map<String, Object> event) {
        try {
            return new CategoryRejectedCommand(
                    (String) event.get("eventId"), (String) event.get("categoryId"),
                    (String) event.get("name"), (String) event.get("proposedByEmail"),
                    (String) event.get("rejectionReason"));
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Malformed category.rejected payload: " + event, e);
        }
    }
}
