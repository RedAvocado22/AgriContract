package com.agricontract.escrow.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.agricontract.escrow.application.dto.LockResultCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.RecordLockResultUseCase;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.inbox.ProcessedEventStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** Reading bank.lock_completed / bank.lock_failed against the frozen schema rules (#102). */
class BankLockResultConsumerTest {
    private final BankLockResultConsumer consumer = new BankLockResultConsumer(new EventEnvelopeCodec(),
            mock(ProcessedEventStore.class), mock(RecordLockResultUseCase.class), mock(TransactionTemplate.class));

    @Test
    void completedResult_becomesALockedCommand() {
        EventEnvelope envelope = envelope("bank.lock_completed", completedPayload());

        LockResultCommand command = consumer.toCommand(envelope, true);

        assertThat(command.locked()).isTrue();
        assertThat(command.amount()).isEqualByComparingTo(new BigDecimal("40000000.00"));
        assertThat(command.ledgerEntryId()).isEqualTo(envelope.payload().get("ledgerEntryId"));
        assertThat(command.failureReason()).isNull();
        assertThat(command.correlationId()).isEqualTo(envelope.correlationId());
    }

    @Test
    void failedResult_carriesTheReason() {
        Map<String, Object> payload = completedPayload();
        payload.remove("ledgerEntryId");
        payload.put("failureReason", "SYSTEM_LOCK_ACTIVE");

        LockResultCommand command = consumer.toCommand(envelope("bank.lock_failed", payload), false);

        assertThat(command.locked()).isFalse();
        assertThat(command.failureReason()).isEqualTo("SYSTEM_LOCK_ACTIVE");
        assertThat(command.ledgerEntryId()).isNull();
    }

    @Test
    void milestoneIdIsOptional() {
        Map<String, Object> payload = completedPayload();
        payload.remove("milestoneId");

        assertThat(consumer.toCommand(envelope("bank.lock_completed", payload), true).locked()).isTrue();
    }

    @Test
    void payloadOutsideTheSchema_isRefused() {
        Map<String, Object> extra = completedPayload();
        extra.put("note", "not in the schema");
        Map<String, Object> missing = completedPayload();
        missing.remove("sourceEventId");
        Map<String, Object> zero = completedPayload();
        zero.put("amount", "0.00");
        Map<String, Object> notAUuid = completedPayload();
        notAUuid.put("ledgerEntryId", "entry-1");

        for (Map<String, Object> payload : new Map[]{extra, missing, zero, notAUuid}) {
            assertThatThrownBy(() -> consumer.toCommand(envelope("bank.lock_completed", payload), true))
                    .isInstanceOf(InvalidEventPayloadException.class);
        }
    }

    @Test
    void wrongTypeOrProducer_isRefused() {
        assertThatThrownBy(() -> consumer.toCommand(envelope("bank.lock_failed", completedPayload()), true))
                .isInstanceOf(InvalidEventPayloadException.class);
        EventEnvelope otherProducer = new EventEnvelope(UUID.randomUUID(), "bank.lock_completed", 1, Instant.now(),
                "escrow-service", "c", UUID.randomUUID().toString(), null, null, completedPayload());
        assertThatThrownBy(() -> consumer.toCommand(otherProducer, true))
                .isInstanceOf(InvalidEventPayloadException.class);
    }

    private static EventEnvelope envelope(String type, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.now(), "bank-service",
                (String) payload.get("contractId"), UUID.randomUUID().toString(), UUID.randomUUID().toString(), null,
                payload);
    }

    private static Map<String, Object> completedPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", UUID.randomUUID().toString());
        payload.put("milestoneId", null);
        payload.put("userId", UUID.randomUUID().toString());
        payload.put("entryType", "LOCK_BUYER_DEPOSIT");
        payload.put("fundType", "BUYER_DEPOSIT");
        payload.put("remedyDecisionId", null);
        payload.put("remedyLegId", null);
        payload.put("amount", "40000000.00");
        payload.put("sourceEventId", UUID.randomUUID().toString());
        payload.put("ledgerEntryId", UUID.randomUUID().toString());
        return payload;
    }
}
