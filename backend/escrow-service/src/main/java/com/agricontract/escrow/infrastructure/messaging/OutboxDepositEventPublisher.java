package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.port.DepositEventPublisher;
import com.agricontract.escrow.domain.model.ActivationDeposits;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    static final String DEPOSIT_LOCK_FAILED = "escrow.deposit_lock_failed";
    static final String LOCKED_STATE = "DEPOSIT_LOCKED";
    static final String LOCK_FAILED_STATE = "LOCK_FAILED";
    /** The contract has no seller deposit (sellerDepositAmount = 0), so there is no seller leg. */
    static final String NOT_REQUIRED_STATE = "NOT_REQUIRED";

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

    /**
     * escrow.deposit_lock_failed. A leg state is DEPOSIT_LOCKED, LOCK_FAILED, or NOT_REQUIRED when the contract has
     * no seller deposit. reason joins the bank's last failure reason of each failed leg.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void depositLockFailed(String contractId, ActivationDeposits deposits, String correlationId,
                                  String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("failedLeg", deposits.failedLeg().name());
        payload.put("buyerDepositState", state(deposits.leg(DepositLegParty.BUYER)));
        payload.put("sellerDepositState", state(deposits.leg(DepositLegParty.SELLER)));
        payload.put("reason", deposits.legs().stream()
                .filter(DepositLeg::isLockFailed)
                .map(l -> l.getParty() + ": " + (l.getLastFailureReason() == null ? "unknown" : l.getLastFailureReason()))
                .collect(Collectors.joining("; ")));

        outbox.append(new EventEnvelope(UUID.randomUUID(), DEPOSIT_LOCK_FAILED, 1, clock.instant(), PRODUCER,
                contractId, correlationId, causationId, null, payload));
    }

    private static String state(Optional<DepositLeg> leg) {
        return leg.map(l -> l.isLocked() ? LOCKED_STATE : LOCK_FAILED_STATE).orElse(NOT_REQUIRED_STATE);
    }
}
