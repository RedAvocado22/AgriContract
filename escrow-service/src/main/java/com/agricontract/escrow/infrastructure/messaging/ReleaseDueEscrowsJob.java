package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReleaseDueEscrowsJob {

    private final EscrowAccountRepository escrowAccountRepository;

    @Scheduled(fixedDelayString = "${escrow.delivery.release-poll-interval-ms:1000}")
    public void releaseDueEscrows() {
        for (EscrowAccount account : escrowAccountRepository.findReleaseEligibleBefore(Instant.now())) {
            if (account.getStatus() != EscrowStatus.DELIVERY_PENDING) {
                continue;
            }
            try {
                account.release();
                escrowAccountRepository.save(account);
                log.info("Released escrow {} after delivery dispute window", account.getEscrowId().value());
            } catch (OptimisticLockingFailureException exception) {
                log.info("Escrow {} changed while release was being scheduled; skipping this poll",
                        account.getEscrowId().value());
            } catch (RuntimeException exception) {
                log.error("Failed to release due escrow {}; continuing with the remaining accounts",
                        account.getEscrowId().value(), exception);
            }
        }
    }
}
