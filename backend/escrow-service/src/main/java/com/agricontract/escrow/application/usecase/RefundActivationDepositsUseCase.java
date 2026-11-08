package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ActivationRefundCommand;
import com.agricontract.escrow.application.port.DepositEventPublisher;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.ActivationDeposits;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * contract.activation_refund_requested (#247, D41/D42): the parties stopped a SIGNED contract whose deposits could not
 * all be locked. Under the contract's row lock every leg leaves the lock path for good:
 * <ul>
 *   <li>LOCKED: its money goes back to its owner (buyer: bank.refund_requested, seller: bank.release_requested);</li>
 *   <li>a lock in flight (REQUESTED): wait for its result, which then returns the money or cancels the leg;</li>
 *   <li>FAILED (lock refused): cancelled, never retried again.</li>
 * </ul>
 * When nothing was locked and nothing is in flight, escrow.activation_refund_completed is written now with no
 * returned leg. Otherwise it is written by the result that settles the last leg. No penalty: nobody breached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundActivationDepositsUseCase {

    private final DepositLegRepository legs;
    private final LockCommandPublisher bankCommands;
    private final DepositEventPublisher events;
    private final Clock clock;

    public void execute(ActivationRefundCommand command) {
        List<DepositLeg> contractLegs = legs.findByContractIdForUpdate(command.contractId());
        if (contractLegs.isEmpty()) {
            throw new InvalidDepositLegException("contract.activation_refund_requested " + command.eventId()
                    + " for contract " + command.contractId() + ", which has no deposit legs");
        }
        boolean settledBefore = new ActivationDeposits(contractLegs).refundSettled();
        Instant now = clock.instant();
        for (DepositLeg leg : contractLegs) {
            DepositLeg.StopOutcome outcome = leg.stopForRefund(now);
            if (outcome == DepositLeg.StopOutcome.ALREADY_STOPPED) {
                continue;
            }
            legs.update(leg);
            if (outcome == DepositLeg.StopOutcome.RETURN_REQUESTED) {
                bankCommands.returnRequested(leg, command.correlationId(), command.eventId());
            }
            log.info("event=activation_deposit_stopped contractId={} leg={} outcome={} trigger={}",
                    command.contractId(), leg.getParty(), outcome, command.trigger());
        }
        ActivationDeposits deposits = new ActivationDeposits(contractLegs);
        if (!settledBefore && deposits.refundSettled()) {
            events.activationRefundCompleted(command.contractId(), deposits, command.correlationId(),
                    command.eventId());
            log.info("event=activation_refund_completed contractId={} returned=0", command.contractId());
        }
    }
}
