package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final JdbcTemplate jdbc;

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

    /**
     * The retry resends the original envelope: its event_outbox row goes back to PENDING (event_id is UNIQUE there,
     * and a new envelope would need a new id, which bank would treat as a new command). created_at is reset so the
     * stale monitor measures from the resend.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resend(DepositLeg leg) {
        int reset = jdbc.update("UPDATE event_outbox SET status = 'PENDING', published_at = NULL, attempts = 0,"
                + " last_error = NULL, created_at = NOW(3) WHERE event_id = ? AND event_type = ?",
                leg.getRequestEventId(), LOCK_REQUESTED);
        if (reset != 1) {
            throw new IllegalStateException("No bank.lock_requested in event_outbox for leg " + leg.getLegId()
                    + " (" + leg.getRequestEventId() + ")");
        }
    }

    /**
     * Same payload shape as the lock (golden-flow-events.yaml bankRefundRequested / bankReleaseRequested): the leg's
     * user, its deposit fundType, the locked amount, no remedy (a technical-failure return, milestone-escrow §6.7).
     */
    @Override
    public void returnRequested(DepositLeg leg, String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", leg.getContractId());
        payload.put("milestoneId", null);
        payload.put("userId", leg.getUserId());
        payload.put("entryType", leg.getParty().returnEntryType());
        payload.put("fundType", leg.getParty().fundType());
        payload.put("remedyDecisionId", null);
        payload.put("remedyLegId", null);
        payload.put("amount", leg.getAmount().amount().toPlainString());
        payload.put("sourceEventId", leg.getReturnRequestEventId());

        outbox.append(new EventEnvelope(UUID.fromString(leg.getReturnRequestEventId()),
                leg.getParty().returnCommand(), 1, clock.instant(), PRODUCER, leg.getContractId(), correlationId,
                causationId, null, payload));
    }

    /** As resend: the original return envelope goes back to PENDING, same eventId. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resendReturn(DepositLeg leg) {
        int reset = jdbc.update("UPDATE event_outbox SET status = 'PENDING', published_at = NULL, attempts = 0,"
                + " last_error = NULL, created_at = NOW(3) WHERE event_id = ? AND event_type = ?",
                leg.getReturnRequestEventId(), leg.getParty().returnCommand());
        if (reset != 1) {
            throw new IllegalStateException("No " + leg.getParty().returnCommand() + " in event_outbox for leg "
                    + leg.getLegId() + " (" + leg.getReturnRequestEventId() + ")");
        }
    }
}
