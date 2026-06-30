package com.agricontract.notification.application.usecase;

import com.agricontract.notification.application.port.EmailPort;
import com.agricontract.notification.domain.model.NotificationLog;
import com.agricontract.notification.domain.model.vo.NotificationChannel;
import com.agricontract.notification.domain.repository.NotificationLogRepository;
import com.agricontract.notification.infrastructure.messaging.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessNotificationUseCaseImpl implements ProcessNotificationUseCase {

    @Value("${notification.admin-email}")
    private String adminEmail;

    private final NotificationLogRepository notificationLogRepository;
    private final EmailPort emailPort;

    private void sendNotification(String eventId, String recipientEmail, String subject, String body) {
        if (notificationLogRepository.existsByEventIdAndUserId(eventId, recipientEmail)) {
            return;
        }

        NotificationLog log = NotificationLog.create(eventId, recipientEmail, NotificationChannel.EMAIL, subject, body);
        notificationLogRepository.save(log);

        try {
            emailPort.sendEmail(recipientEmail, subject, body);
            log.markSent();
        } catch (Exception exception) {
            log.markFailed();
            throw new RuntimeException(exception);
        }

        notificationLogRepository.save(log);
    }

    @Override
    public void handleContractSigned(ContractSignedEvent event) {
        String subject = "[AgriContract] Contract has been signed";
        String body = "Contract " + event.getContractId() + " has been successfully signed.";

        sendNotification(event.getEventId().toString(), event.getBuyerEmail(), subject, body);
        sendNotification(event.getEventId().toString(), event.getSellerEmail(), subject, body);
    }


    @Override
    public void handleContractCancelled(ContractCancelledEvent event) {
        String subject = "[AgriContract] Contract has been cancelled";
        String body = "Contract " + event.getContractId() + " has been successfully cancelled.";

        sendNotification(event.getEventId().toString(), event.getBuyerEmail(), subject, body);
        sendNotification(event.getEventId().toString(), event.getSellerEmail(), subject, body);

    }

    @Override
    public void handleContractDelivered(ContractDeliveredEvent event) {
        String subject = "[AgriContract] Buyer confirmed receipt of goods";
        String body = "Contract " + event.getContractId() + " has been successfully delivered.";

        sendNotification(event.getEventId().toString(), event.getSellerEmail(), subject, body);

    }

    @Override
    public void handleContractDisputed(ContractDisputedEvent event) {
        String subject = "[AgriContract] [ADMIN] New dispute requires resolution";
        String body = "Contract " + event.getContractId() + " has been successfully disputed.";

        sendNotification(event.getEventId().toString(), adminEmail, subject, body);
        sendNotification(event.getEventId().toString(), event.getSellerEmail(), subject, body);
        sendNotification(event.getEventId().toString(), event.getBuyerEmail(), subject, body);

    }

    @Override
    public void handleEscrowLocked(EscrowLockedEvent event) {
        String subject = "[AgriContract] Escrow locked — proceed with delivery";
        String body = "Escrow " + event.getEscrowId() + " has been successfully locked.";

        sendNotification(event.getEventId().toString(), event.getSellerEmail(), subject, body);

    }

    @Override
    public void handleEscrowReleased(EscrowReleasedEvent event) {
        String subject = "[AgriContract] Payment has been released";
        String body = "Escrow " + event.getEscrowId() + " has been successfully released.";

        sendNotification(event.getEventId().toString(), event.getSellerEmail(), subject, body);
        sendNotification(event.getEventId().toString(), event.getBuyerEmail(), subject, body);

    }

    @Override
    public void handleEscrowPenalized(EscrowPenalizedEvent event) {
        String subject = "[AgriContract]  Contract penalty notification";
        String body = "Escrow " + event.getEscrowId() + " has been successfully penalized.";

        String recipient = event.getPenalizedParty() == PenalizedParty.BUYER
                ? event.getBuyerEmail()
                : event.getSellerEmail();
        sendNotification(event.getEventId().toString(), recipient, subject, body);

    }
}
