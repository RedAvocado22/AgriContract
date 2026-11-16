package com.agricontract.reputation.infrastructure.messaging;

import com.agricontract.reputation.application.port.LockStateStore;
import com.agricontract.reputation.application.usecase.LockEventLifecycle;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reputation.lock-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ExpireLocksJob {
    private static final Logger LOG = LoggerFactory.getLogger(ExpireLocksJob.class);
    private final LockStateStore states;
    private final LockEventLifecycle lifecycle;
    private final int batchSize;
    public ExpireLocksJob(LockStateStore states, LockEventLifecycle lifecycle,
                          @Value("${reputation.lock-expiry.batch-size:100}") int batchSize) {
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("Expiry batch size must be 1..1000");
        this.states = states; this.lifecycle = lifecycle; this.batchSize = batchSize;
    }
    @Scheduled(fixedDelayString = "${reputation.lock-expiry.interval:PT1M}",
            initialDelayString = "${reputation.lock-expiry.interval:PT1M}")
    public void run() {
        String correlation = UUID.randomUUID().toString();
        Instant cutoff = states.now();
        LockStateStore.DueLock cursor = null;
        while (true) {
            var page = states.dueUsers(cutoff, cursor, batchSize);
            if (page.isEmpty()) return;
            for (var candidate : page) {
                try { lifecycle.expire(candidate.userId(), correlation); }
                catch (RuntimeException failure) {
                    LOG.error("Lock expiry transaction failed for user {}", candidate.userId(), failure);
                }
            }
            cursor = page.getLast(); // Advance over selected rows even if a transaction failed.
        }
    }
}
