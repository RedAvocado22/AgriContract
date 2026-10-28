package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * bank.lock_requested (golden-flow-events.yaml bankLockRequested). The envelope eventId is the leg's
 * requestEventId and payload.sourceEventId repeats it: bank-service dedups on sourceEventId and requires it to equal
 * the eventId, so a resend of the same leg (#102) must reuse this id, never mint a new one (D27).
 */
@Component
@RequiredArgsConstructor
public class OutboxLockCommandPublisher implements LockCommandPublisher {

    static final String PRODUCER = "escrow-service";
    static final String LOCK_REQUESTED = "bank.lock_requested";

    private final OutboxWriter outbox;
    private final Clock clock;

    @Override
    public void lockRequested(DepositLeg leg, String correlationId, String causationId) {
        // Nullable fields stay on the wire as null: the schema lists them as required.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", leg.getContractId());
        payload.put("milestoneId", null);            // contract-level deposit, not a milestone lock
        payload.put("userId", leg.getUserId());
        payload.put("entryType", leg.getParty().entryType());
        payload.put("fundType", leg.getParty().fundType());
        payload.put("remedyDecisionId", null);
        payload.put("remedyLegId", null);
        payload.put("amount", leg.getAmount().amount().toPlainString());
        payload.put("sourceEventId", leg.getRequestEventId());

        // The bank pool is per contract (bank design §2b.3), so the contract is the aggregate.
        outbox.append(new EventEnvelope(UUID.fromString(leg.getRequestEventId()), LOCK_REQUESTED, 1,
                clock.instant(), PRODUCER, leg.getContractId(), correlationId, causationId, null, payload));
    }
}
