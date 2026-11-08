package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.LockResultCommand;
import com.agricontract.escrow.application.port.DepositEventPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
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
 * </ul>
 * A result that matches no leg, or not the leg it names, is refused (DLQ): it is a producer bug, not a retry case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordLockResultUseCaseImpl implements RecordLockResultUseCase {

    private final DepositLegRepository legs;
    private final DepositEventPublisher events;
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

        if (command.locked()) {
            if (!leg.markLocked(command.ledgerEntryId(), now)) {
                log.info("event=deposit_lock_result_duplicate contractId={} leg={} sourceEventId={}",
                        command.contractId(), leg.getParty(), command.sourceEventId());
                return;
            }
            legs.update(leg);
            log.info("event=deposit_leg_locked contractId={} leg={} ledgerEntryId={}",
                    command.contractId(), leg.getParty(), command.ledgerEntryId());
            if (contractLegs.stream().allMatch(DepositLeg::isLocked)) {
                boolean hasSellerLeg = contractLegs.stream().anyMatch(l -> l.getParty() == DepositLegParty.SELLER);
                events.depositLocked(command.contractId(), hasSellerLeg, command.correlationId(), command.eventId());
                log.info("event=deposits_locked contractId={} legs={}", command.contractId(), contractLegs.size());
            }
            return;
        }

        DepositLeg.FailureOutcome outcome = leg.markFailed(command.failureReason(), now, retrySchedule);
        if (outcome == DepositLeg.FailureOutcome.IGNORED_ALREADY_LOCKED) {
            log.warn("event=deposit_lock_late_failure_ignored contractId={} leg={} sourceEventId={} reason={}",
                    command.contractId(), leg.getParty(), command.sourceEventId(), command.failureReason());
            return;
        }
        legs.update(leg);
        log.warn("event=deposit_leg_lock_failed contractId={} leg={} failures={} nextAttemptAt={} reason={}",
                command.contractId(), leg.getParty(), leg.getFailedAttempts(), leg.getNextAttemptAt(),
                command.failureReason());
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
