package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Only the legs that failed are retried, never a LOCKED one (design §3.1 "retry theo LEG"). The resend is the
 * original request, same eventId and payload, so bank-service dedups on it (D27). Under the same contract row lock
 * as the results, so a result arriving meanwhile is never overwritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryDepositLocksUseCaseImpl implements RetryDepositLocksUseCase {

    private static final int BATCH = 100;

    private final DepositLegRepository legs;
    private final LockCommandPublisher lockCommands;
    private final Clock clock;

    @Override
    public List<String> contractsWithRetryDue() {
        return legs.findContractsWithRetryDue(clock.instant(), BATCH);
    }

    @Override
    public int retryDue(String contractId) {
        Instant now = clock.instant();
        int resent = 0;
        for (DepositLeg leg : legs.findByContractIdForUpdate(contractId)) {
            if (leg.isReturnRetryDue(now)) {
                leg.returnRetryRequested(now);
                legs.update(leg);
                lockCommands.resendReturn(leg);
                resent++;
                log.info("event=deposit_return_retried contractId={} leg={} failures={} sourceEventId={}",
                        contractId, leg.getParty(), leg.getFailedAttempts(), leg.getReturnRequestEventId());
                continue;
            }
            if (!leg.isRetryDue(now)) {
                continue;
            }
            leg.retryRequested(now);
            legs.update(leg);
            lockCommands.resend(leg);
            resent++;
            log.info("event=deposit_lock_retried contractId={} leg={} failures={} sourceEventId={}",
                    contractId, leg.getParty(), leg.getFailedAttempts(), leg.getRequestEventId());
        }
        return resent;
    }
}
