package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.port.DepositEventPublisher;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * escrow.deposit_locked (golden-flow-events.yaml escrowDepositLocked) to agricontract.escrow, for contract-service
 * to move the contract to ACTIVE (#103). Both states are DEPOSIT_LOCKED: it is written only once every required
 * leg is. sellerDepositState is null, key still present, when the contract has no seller deposit.
 */
@Component
@RequiredArgsConstructor
public class OutboxDepositEventPublisher implements DepositEventPublisher {

    static final String PRODUCER = "escrow-service";
    static final String DEPOSIT_LOCKED = "escrow.deposit_locked";
    static final String LOCKED_STATE = "DEPOSIT_LOCKED";

    private final OutboxWriter outbox;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void depositLocked(String contractId, boolean hasSellerLeg, String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("buyerDepositState", LOCKED_STATE);
        payload.put("sellerDepositState", hasSellerLeg ? LOCKED_STATE : null);

        // causationId = the bank result that completed the set; correlationId continues the activation chain.
        outbox.append(new EventEnvelope(UUID.randomUUID(), DEPOSIT_LOCKED, 1, clock.instant(), PRODUCER, contractId,
                correlationId, causationId, null, payload));
    }
}
