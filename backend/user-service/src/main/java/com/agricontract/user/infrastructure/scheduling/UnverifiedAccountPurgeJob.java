package com.agricontract.user.infrastructure.scheduling;

import com.agricontract.user.application.usecase.PurgeUnverifiedAccountsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the D59 purge every few minutes. A run that finds nothing due makes one Keycloak query and stops.
@Component
@ConditionalOnProperty(name = "user.unverified-account.purge.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class UnverifiedAccountPurgeJob {
    private final PurgeUnverifiedAccountsUseCase purge;

    @Scheduled(fixedDelayString = "${user.unverified-account.purge.interval-ms:300000}",
            initialDelayString = "${user.unverified-account.purge.interval-ms:300000}")
    public void run() {
        int deleted = purge.execute();
        if (deleted > 0) {
            log.info("event=unverified_account_purge_run deleted={}", deleted);
        }
    }
}
