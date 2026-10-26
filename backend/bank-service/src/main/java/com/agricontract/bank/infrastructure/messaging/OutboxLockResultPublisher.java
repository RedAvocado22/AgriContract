package com.agricontract.bank.infrastructure.messaging;

import com.agricontract.bank.application.dto.LockFundsCommand;
import com.agricontract.bank.application.port.LockResultPublisher;
import com.agricontract.bank.domain.model.LedgerEntry;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * bank.lock_completed / bank.lock_failed (golden-flow-events.yaml). Each result, a re-sent one included, gets a new
 * eventId; causationId is the request eventId and sourceEventId is echoed, so escrow matches the result to its
 * deposit leg by sourceEventId, not by eventId.
 */
@Component
@RequiredArgsConstructor
public class OutboxLockResultPublisher implements LockResultPublisher {

    static final String PRODUCER = "bank-service";
    static final String COMPLETED = "bank.lock_completed";
    static final String FAILED = "bank.lock_failed";

    private final OutboxWriter outbox;
    private final Clock clock;

    @Override
    public void completed(LockFundsCommand command, LedgerEntry entry) {
        Map<String, Object> payload = basePayload(command);
        payload.put("ledgerEntryId", entry.getEntryId());
        outbox.append(envelope(COMPLETED, command, payload));
    }

    @Override
    public void failed(LockFundsCommand command, String reason) {
        Map<String, Object> payload = basePayload(command);
        payload.put("failureReason", reason);
        outbox.append(envelope(FAILED, command, payload));
    }

    // Nullable fields stay on the wire as null: the schema lists them as required.
    private static Map<String, Object> basePayload(LockFundsCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", command.contractId());
        payload.put("milestoneId", command.milestoneId());
        payload.put("userId", command.userId());
        payload.put("entryType", command.entryType().name());
        payload.put("fundType", command.fundType().name());
        payload.put("remedyDecisionId", command.remedyDecisionId());
        payload.put("remedyLegId", command.remedyLegId());
        payload.put("amount", money(command.amount()));
        payload.put("sourceEventId", command.sourceEventId());
        return payload;
    }

    private EventEnvelope envelope(String eventType, LockFundsCommand command, Map<String, Object> payload) {
        // The pooled "account" is the contract (bank design §2b.3), so the contract is the aggregate.
        return new EventEnvelope(UUID.randomUUID(), eventType, 1, clock.instant(), PRODUCER, command.contractId(),
                command.correlationId(), command.requestEventId(), null, payload);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
