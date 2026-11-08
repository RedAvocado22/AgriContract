package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ReturnResultCommand;
import com.agricontract.escrow.application.port.DepositEventPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.ActivationDeposits;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.RetrySchedule;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * A bank result for an activation deposit return (#247). Under the contract's row lock:
 * <ul>
 *   <li>returned: the leg is RETURNED; the result that settles the last leg writes
 *       escrow.activation_refund_completed in the same transaction (exactly once);</li>
 *   <li>refused: the same return command is resent per the deposit-lock schedule; with no resend left the leg stays
 *       RETURN_FAILED, the contract stays ACTIVATION_REFUND_PENDING and the error log alerts OPERATOR.</li>
 * </ul>
 * A result that matches no return, or not the leg it names, is refused (DLQ): a producer bug, not a retry case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordReturnResultUseCase {

    private final DepositLegRepository legs;
    private final DepositEventPublisher events;
    private final RetrySchedule retrySchedule;
    private final Clock clock;

    public void execute(ReturnResultCommand command) {
        List<DepositLeg> contractLegs = legs.findByContractIdForUpdate(command.contractId());
        DepositLeg leg = contractLegs.stream()
                .filter(l -> command.sourceEventId().equals(l.getReturnRequestEventId()))
                .findFirst()
                .orElseThrow(() -> new InvalidDepositLegException("No deposit return of contract "
                        + command.contractId() + " was requested as " + command.sourceEventId()));
        if (!leg.getUserId().equals(command.userId())
                || !leg.getParty().returnEntryType().equals(command.entryType())
                || leg.getAmount().amount().compareTo(command.amount()) != 0) {
            throw new InvalidDepositLegException("Result " + command.eventId() + " does not match the return of leg "
                    + leg.getLegId() + " (user, entryType or amount)");
        }
        boolean settledBefore = new ActivationDeposits(contractLegs).refundSettled();
        Instant now = clock.instant();

        if (command.returned()) {
            if (!leg.markReturned(command.ledgerEntryId(), now)) {
                log.info("event=deposit_return_duplicate contractId={} leg={}", command.contractId(), leg.getParty());
                return;
            }
            legs.update(leg);
            log.info("event=deposit_returned contractId={} leg={} ledgerEntryId={}", command.contractId(),
                    leg.getParty(), command.ledgerEntryId());
            ActivationDeposits deposits = new ActivationDeposits(contractLegs);
            if (!settledBefore && deposits.refundSettled()) {
                events.activationRefundCompleted(command.contractId(), deposits, command.correlationId(),
                        command.eventId());
                log.info("event=activation_refund_completed contractId={}", command.contractId());
            }
            return;
        }

        DepositLeg.ReturnOutcome outcome = leg.markReturnFailed(command.failureReason(), now, retrySchedule);
        if (outcome == DepositLeg.ReturnOutcome.IGNORED_ALREADY_RETURNED) {
            log.warn("event=deposit_return_late_failure_ignored contractId={} leg={}", command.contractId(),
                    leg.getParty());
            return;
        }
        legs.update(leg);
        if (outcome == DepositLeg.ReturnOutcome.RETRIES_EXHAUSTED) {
            log.error("event=deposit_return_failed_alert contractId={} leg={} amount={} reason={}: money still locked,"
                            + " contract stays ACTIVATION_REFUND_PENDING, OPERATOR must act", command.contractId(),
                    leg.getParty(), leg.getAmount().amount(), command.failureReason());
        } else {
            log.warn("event=deposit_return_refused contractId={} leg={} failures={} nextAttemptAt={} reason={}",
                    command.contractId(), leg.getParty(), leg.getFailedAttempts(), leg.getNextAttemptAt(),
                    command.failureReason());
        }
    }
}
