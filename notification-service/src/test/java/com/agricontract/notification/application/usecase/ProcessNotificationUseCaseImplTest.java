package com.agricontract.notification.application.usecase;

import com.agricontract.notification.application.dto.CategoryApprovedCommand;
import com.agricontract.notification.application.dto.CategoryRejectedCommand;
import com.agricontract.notification.application.port.EmailPort;
import com.agricontract.notification.domain.model.NotificationLog;
import com.agricontract.notification.domain.model.vo.NotificationChannel;
import com.agricontract.notification.domain.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessNotificationUseCaseImplTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private EmailPort emailPort;

    private ProcessNotificationUseCaseImpl useCase;

    @Test
    void handleCategoryApproved_sendsEmailToProposer() {
        useCase = new ProcessNotificationUseCaseImpl(notificationLogRepository, emailPort);
        when(notificationLogRepository.findByEventIdAndUserId("event-1", "seller@test.com"))
                .thenReturn(Optional.empty());

        useCase.handleCategoryApproved(new CategoryApprovedCommand("event-1", "category-1", "Ca Phe", "seller@test.com"));

        verify(emailPort).sendEmail(eq("seller@test.com"), contains("approved"), contains("Ca Phe"));
        verify(notificationLogRepository, times(2)).save(any());
    }

    @Test
    void handleCategoryApproved_alreadySent_skipsResend() {
        useCase = new ProcessNotificationUseCaseImpl(notificationLogRepository, emailPort);
        NotificationLog sent = NotificationLog.create("event-1", "seller@test.com", NotificationChannel.EMAIL, "s", "b");
        sent.markSent();
        when(notificationLogRepository.findByEventIdAndUserId("event-1", "seller@test.com"))
                .thenReturn(Optional.of(sent));

        useCase.handleCategoryApproved(new CategoryApprovedCommand("event-1", "category-1", "Ca Phe", "seller@test.com"));

        verify(emailPort, never()).sendEmail(any(), any(), any());
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void handleCategoryRejected_sendsEmailWithReason() {
        useCase = new ProcessNotificationUseCaseImpl(notificationLogRepository, emailPort);
        when(notificationLogRepository.findByEventIdAndUserId("event-2", "seller@test.com"))
                .thenReturn(Optional.empty());

        useCase.handleCategoryRejected(new CategoryRejectedCommand(
                "event-2", "category-1", "Ca Phe", "seller@test.com", "trung ten"));

        verify(emailPort).sendEmail(eq("seller@test.com"), contains("rejected"), contains("trung ten"));
    }
}
