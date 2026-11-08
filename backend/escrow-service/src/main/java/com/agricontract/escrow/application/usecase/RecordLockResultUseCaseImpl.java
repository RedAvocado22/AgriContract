package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.LockResultCommand;
import com.agricontract.escrow.application.port.DepositEventPublisher;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.ActivationDeposits;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.escrow.domain.model.DepositLegStatus;
import com.agricontract.escrow.domain.model.RetrySchedule;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Applies one bank result to its deposit leg (#102, D39):
 * <ul>
 *   <li>Every leg of the contract is row-locked first, so two results of one contract run one after the other and
 *       the one that completes the set always sees the other.</li>
 *   <li>Locked: the leg becomes LOCKED (final). If that makes every required leg LOCKED, escrow.deposit_locked is
 *       written in the same transaction: exactly once, since a duplicate result changes no leg.</li>
 *   <li>Failed: counted and a resend scheduled per the RetrySchedule; ignored when the leg is already LOCKED,
 *       because the bank ledger holds the money.</li>
 *   <li>After the parties cancelled (#247): a lock that lands anyway is returned at once; a lock that fails cancels
 *       the leg, and the result that settles the last leg writes escrow.activation_refund_completed.</li>
 *   <li>The result that leaves nothing in progress while a leg's lock failed with no retry left writes
 *       escrow.deposit_lock_failed (#247). Only that transition writes it, so a duplicate or replayed result does
 *       not; a later retry cycle (contract.deposit_lock_retry_requested) that fails again reports again.</li>
 * </ul>
 * A result that matches no leg, or not the leg it names, is refused (DLQ): it is a producer bug, not a retry case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordLockResultUseCaseImpl implements RecordLockResultUseCase {

    private final DepositLegRepository legs;
    private final DepositEventPublisher events;
    private final LockCommandPublisher bankCommands;
    private final RetrySchedule retrySchedule;
    private final Clock clock;

    @Override
    public void execute(LockResultCommand command) {
        List<DepositLeg> contractLegs = legs.findByContractIdForUpdate(command.contractId());
        DepositLeg leg = contractLegs.stream()
                .filter(l -> l.getRequestEventId().equals(command.sourceEventId()))
                .findFirst()
                .orElseThrow(() -> new InvalidDepositLegException("No deposit leg of contract " + command.contractId()
                        + " was requested as " + command.sourceEventId()));
        requireSameRequest(leg, command);
        Instant now = clock.instant();
        boolean failedBefore = new ActivationDeposits(contractLegs).lockFailed();
        boolean refundSettledBefore = new ActivationDeposits(contractLegs).refundSettled();

        if (command.locked()) {
            if (!leg.markLocked(command.ledgerEntryId(), now)) {
                log.info("event=deposit_lock_result_duplicate contractId={} leg={} sourceEventId={}",
                        command.contractId(), leg.getParty(), command.sourceEventId());
                return;
            }
            legs.update(leg);
            if (leg.getStatus() == DepositLegStatus.RETURN_REQUESTED) {
                // Locked after the parties cancelled (#247): the bank ledger holds the money, so it goes back now.
                bankCommands.returnRequested(leg, command.correlationId(), command.eventId());
                log.warn("event=deposit_locked_after_cancel_returned contractId={} leg={} ledgerEntryId={}",
                        command.contractId(), leg.getParty(), command.ledgerEntryId());
                return;
            }
            log.info("event=deposit_leg_locked contractId={} leg={} ledgerEntryId={}",
                    command.contractId(), leg.getParty(), command.ledgerEntryId());
            ActivationDeposits deposits = new ActivationDeposits(contractLegs);
            if (deposits.allLocked()) {
                boolean hasSellerLeg = deposits.leg(DepositLegParty.SELLER).isPresent();
                events.depositLocked(command.contractId(), hasSellerLeg, command.correlationId(), command.eventId());
                log.info("event=deposits_locked contractId={} legs={}", command.contractId(), contractLegs.size());
            }
            reportIfLockFailedNow(failedBefore, deposits, command);
            return;
        }

        DepositLeg.FailureOutcome outcome = leg.markFailed(command.failureReason(), now, retrySchedule);
        if (outcome == DepositLeg.FailureOutcome.IGNORED_CANCELLED) {
            log.info("event=deposit_lock_failure_after_cancel_ignored contractId={} leg={}", command.contractId(),
                    leg.getParty());
            return;
        }
        if (outcome == DepositLeg.FailureOutcome.CANCELLED) {
            legs.update(leg);
            ActivationDeposits deposits = new ActivationDeposits(contractLegs);
            log.info("event=deposit_leg_cancelled contractId={} leg={}", command.contractId(), leg.getParty());
            if (!refundSettledBefore && deposits.refundSettled()) {
                events.activationRefundCompleted(command.contractId(), deposits, command.correlationId(),
                        command.eventId());
                log.info("event=activation_refund_completed contractId={}", command.contractId());
            }
            return;
        }
        if (outcome == DepositLeg.FailureOutcome.IGNORED_ALREADY_LOCKED) {
            log.warn("event=deposit_lock_late_failure_ignored contractId={} leg={} sourceEventId={} reason={}",
                    command.contractId(), leg.getParty(), command.sourceEventId(), command.failureReason());
            return;
        }
        legs.update(leg);
        log.warn("event=deposit_leg_lock_failed contractId={} leg={} failures={} nextAttemptAt={} reason={}",
                command.contractId(), leg.getParty(), leg.getFailedAttempts(), leg.getNextAttemptAt(),
                command.failureReason());
        reportIfLockFailedNow(failedBefore, new ActivationDeposits(contractLegs), command);
    }

    private void reportIfLockFailedNow(boolean failedBefore, ActivationDeposits deposits, LockResultCommand command) {
        if (failedBefore || !deposits.lockFailed()) {
            return;
        }
        events.depositLockFailed(command.contractId(), deposits, command.correlationId(), command.eventId());
        log.warn("event=deposit_lock_failed contractId={} failedLeg={}", command.contractId(), deposits.failedLeg());
    }

    private static void requireSameRequest(DepositLeg leg, LockResultCommand command) {
        if (!leg.getUserId().equals(command.userId())
                || !leg.getParty().entryType().equals(command.entryType())
                || leg.getAmount().amount().compareTo(command.amount()) != 0) {
            throw new InvalidDepositLegException("Result " + command.eventId() + " does not match deposit leg "
                    + leg.getLegId() + " (user, entryType or amount)");
        }
    }
}
