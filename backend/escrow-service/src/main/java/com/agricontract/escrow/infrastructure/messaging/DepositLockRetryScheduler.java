package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.usecase.RetryDepositLocksUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Looks for FAILED deposit legs whose retry is due and resends them, one transaction per contract, so one contract
 * that fails does not hold back the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositLockRetryScheduler {

    private final RetryDepositLocksUseCase retries;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${escrow.deposit-lock.retry-poll-interval}")
    public void retryDueLegs() {
        for (String contractId : retries.contractsWithRetryDue()) {
            try {
                transactionTemplate.executeWithoutResult(status -> retries.retryDue(contractId));
            } catch (RuntimeException e) {
                log.error("event=deposit_lock_retry_error contractId={}: {}", contractId, e.getMessage(), e);
            }
        }
    }
}
