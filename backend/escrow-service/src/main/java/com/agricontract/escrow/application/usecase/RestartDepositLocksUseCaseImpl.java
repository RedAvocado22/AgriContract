package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.DepositLockRetryCommand;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * OPERATOR RetryDepositLock after escrow.deposit_lock_failed (milestone-escrow §3.1 step 3, D42). Under the contract's
 * row lock, each leg whose lock failed goes back to REQUESTED with a fresh retry cycle and its original
 * bank.lock_requested is sent again. LOCKED legs are never requested again. If no leg has failed (a late
 * bank.lock_completed already locked it, or the cycle is still running) nothing is sent. A contract escrow never saw
 * is refused (DLQ): it is a producer bug.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestartDepositLocksUseCaseImpl implements RestartDepositLocksUseCase {

    private final DepositLegRepository legs;
    private final LockCommandPublisher lockCommands;
    private final Clock clock;

    @Override
    public int execute(DepositLockRetryCommand command) {
        List<DepositLeg> contractLegs = legs.findByContractIdForUpdate(command.contractId());
        if (contractLegs.isEmpty()) {
            throw new InvalidDepositLegException("contract.deposit_lock_retry_requested " + command.eventId()
                    + " for contract " + command.contractId() + ", which has no deposit legs");
        }
        Instant now = clock.instant();
        int resent = 0;
        for (DepositLeg leg : contractLegs) {
            if (!leg.restartLockCycle(now)) {
                continue;
            }
            legs.update(leg);
            lockCommands.resend(leg);
            resent++;
            log.info("event=deposit_lock_cycle_restarted contractId={} leg={} sourceEventId={} operator={}",
                    command.contractId(), leg.getParty(), leg.getRequestEventId(), command.operatorUserId());
        }
        if (resent == 0) {
            log.info("event=deposit_lock_retry_nothing_failed contractId={} eventId={}", command.contractId(),
                    command.eventId());
        }
        return resent;
    }
}
