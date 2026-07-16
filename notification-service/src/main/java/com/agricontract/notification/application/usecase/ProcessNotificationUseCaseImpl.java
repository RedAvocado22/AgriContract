package com.agricontract.notification.application.usecase;

import com.agricontract.notification.application.dto.*;
import com.agricontract.notification.application.port.EmailPort;
import com.agricontract.notification.domain.model.NotificationLog;
import com.agricontract.notification.domain.model.vo.NotificationChannel;
import com.agricontract.notification.domain.model.vo.NotificationStatus;
import com.agricontract.notification.domain.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessNotificationUseCaseImpl implements ProcessNotificationUseCase {

    @Value("${notification.admin-email}")
    private String adminEmail;

    private final NotificationLogRepository notificationLogRepository;
    private final EmailPort emailPort;

    private void sendNotification(String eventId, String recipientEmail, String subject, String body) {
        NotificationLog log = notificationLogRepository.findByEventIdAndUserId(eventId, recipientEmail)
                .orElseGet(() -> NotificationLog.create(eventId, recipientEmail, NotificationChannel.EMAIL, subject, body));

        if (log.getStatus() == NotificationStatus.SENT) {
            return;
        }

        try {
            notificationLogRepository.save(log);
        } catch (DataIntegrityViolationException e) {
            // uq_event_user (event_id, user_id) tripped — another concurrent delivery already
            // inserted this log first, so this thread must not send a duplicate email.
            return;
        }

        try {
            emailPort.sendEmail(recipientEmail, subject, body);
            log.markSent();
        } catch (Exception exception) {
            log.markFailed();
            throw new RuntimeException(exception);
        } finally {
            notificationLogRepository.save(log);
        }
    }

    @Override
    public void handleContractSigned(ContractSignedCommand command) {
        String subject = "[AgriContract] Contract has been signed";
        String body = "Contract " + command.contractId() + " has been successfully signed.";

        sendNotification(command.eventId(), command.buyerEmail(), subject, body);
        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
    }

    @Override
    public void handleContractCancelled(ContractCancelledCommand command) {
        String subject = "[AgriContract] Contract has been cancelled";
        String body = "Contract " + command.contractId() + " has been cancelled. Reason: " + command.reason();

        sendNotification(command.eventId(), command.buyerEmail(), subject, body);
        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
    }

    @Override
    public void handleContractDelivered(ContractDeliveredCommand command) {
        String subject = "[AgriContract] Buyer confirmed receipt of goods";
        String body = "Contract " + command.contractId() + " has been successfully delivered.";

        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
    }

    @Override
    public void handleContractDisputed(ContractDisputedCommand command) {
        String subject = "[AgriContract] [ADMIN] New dispute requires resolution";
        String body = "Contract " + command.contractId() + " has been disputed. Reason: " + command.reason();

        sendNotification(command.eventId(), adminEmail, subject, body);
        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
        sendNotification(command.eventId(), command.buyerEmail(), subject, body);
    }

    @Override
    public void handleEscrowLocked(EscrowLockedCommand command) {
        String subject = "[AgriContract] Escrow locked — proceed with delivery";
        String body = "Escrow " + command.escrowId() + " has been successfully locked.";

        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
    }

    @Override
    public void handleEscrowReleased(EscrowReleasedCommand command) {
        String subject = "[AgriContract] Payment has been released";
        String body = "Escrow " + command.escrowId() + " has been successfully released.";

        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
        sendNotification(command.eventId(), command.buyerEmail(), subject, body);
    }

    @Override
    public void handleEscrowPenalized(EscrowPenalizedCommand command) {
        String subject = "[AgriContract] Contract penalty notification";
        String body = "Escrow " + command.escrowId() + " has been successfully penalized.";

        sendNotification(command.eventId(), command.penalizedPartyEmail(), subject, body);
    }

    @Override
    public void handleEscrowArbitrated(EscrowArbitratedCommand command) {
        String subject = "[AgriContract] Dispute arbitration completed";
        String body = "Escrow " + command.escrowId()
                + " for contract " + command.contractId()
                + " has been arbitrated. Decision: " + command.justification();

        sendNotification(command.eventId(), command.buyerEmail(), subject, body);
        sendNotification(command.eventId(), command.sellerEmail(), subject, body);
    }

    @Override
    public void handleCategoryApproved(CategoryApprovedCommand command) {
        String subject = "[AgriContract] Category proposal approved";
        String body = "Your proposed category \"" + command.name() + "\" has been approved.";

        sendNotification(command.eventId(), command.proposedByEmail(), subject, body);
    }

    @Override
    public void handleCategoryRejected(CategoryRejectedCommand command) {
        String subject = "[AgriContract] Category proposal rejected";
        String body = "Your proposed category \"" + command.name() + "\" has been rejected. Reason: " + command.rejectionReason();

        sendNotification(command.eventId(), command.proposedByEmail(), subject, body);
    }
}
