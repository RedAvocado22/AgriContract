package com.agricontract.notification.infrastructure.messaging;

import com.agricontract.notification.application.dto.CategoryApprovedCommand;
import com.agricontract.notification.application.dto.CategoryRejectedCommand;
import com.agricontract.notification.application.dto.EscrowArbitratedCommand;
import com.agricontract.notification.application.exception.InvalidEventPayloadException;
import com.agricontract.notification.application.usecase.ProcessNotificationUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private ProcessNotificationUseCase processNotificationUseCase;

    private NotificationEventConsumer consumer;

    @Test
    void onCategoryApproved_wellFormedPayload_delegatesParsedCommand() {
        consumer = new NotificationEventConsumer(processNotificationUseCase);
        Map<String, Object> event = Map.of(
                "eventId", "event-1", "categoryId", "category-1",
                "name", "Ca Phe", "proposedByEmail", "seller@test.com");

        consumer.onCategoryApproved(event);

        ArgumentCaptor<CategoryApprovedCommand> captor = ArgumentCaptor.forClass(CategoryApprovedCommand.class);
        verify(processNotificationUseCase).handleCategoryApproved(captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo("category-1");
        assertThat(captor.getValue().proposedByEmail()).isEqualTo("seller@test.com");
    }

    @Test
    void onCategoryApproved_malformedPayload_throwsInvalidEventPayload() {
        consumer = new NotificationEventConsumer(processNotificationUseCase);
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "event-1");
        event.put("categoryId", 12345); // wrong type -> ClassCastException on cast to String

        assertThatThrownBy(() -> consumer.onCategoryApproved(event))
                .isInstanceOf(InvalidEventPayloadException.class);

        verify(processNotificationUseCase, never()).handleCategoryApproved(any());
    }

    @Test
    void onCategoryRejected_wellFormedPayload_delegatesParsedCommand() {
        consumer = new NotificationEventConsumer(processNotificationUseCase);
        Map<String, Object> event = Map.of(
                "eventId", "event-2", "categoryId", "category-1", "name", "Ca Phe",
                "proposedByEmail", "seller@test.com", "rejectionReason", "trung ten");

        consumer.onCategoryRejected(event);

        ArgumentCaptor<CategoryRejectedCommand> captor = ArgumentCaptor.forClass(CategoryRejectedCommand.class);
        verify(processNotificationUseCase).handleCategoryRejected(captor.capture());
        assertThat(captor.getValue().rejectionReason()).isEqualTo("trung ten");
    }

    @Test
    void onEscrowArbitrated_wellFormedPayload_delegatesDecision() {
        consumer = new NotificationEventConsumer(processNotificationUseCase);
        Map<String, Object> event = Map.of(
                "eventId", "event-3", "escrowId", "escrow-1", "contractId", "contract-1",
                "buyerEmail", "buyer@test.com", "sellerEmail", "seller@test.com",
                "justification", "Inspection result");

        consumer.onEscrowArbitrated(event);

        ArgumentCaptor<EscrowArbitratedCommand> captor = ArgumentCaptor.forClass(EscrowArbitratedCommand.class);
        verify(processNotificationUseCase).handleEscrowArbitrated(captor.capture());
        assertThat(captor.getValue().contractId()).isEqualTo("contract-1");
        assertThat(captor.getValue().justification()).isEqualTo("Inspection result");
    }
}
