package com.agricontract.product.infrastructure.scheduling;

import com.agricontract.product.application.usecase.ReapOrphanReservationsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Periodically releases orphan reservations (design §8b.2). The grace period gives compensation release and
 * the contract.created outbox time to land before a RESERVED row counts as abandoned.
 */
@Slf4j
@Component
public class OrphanReservationReaperJob {

    private final ReapOrphanReservationsUseCase reapOrphanReservationsUseCase;
    private final Clock clock;
    private final Duration grace;

    public OrphanReservationReaperJob(ReapOrphanReservationsUseCase reapOrphanReservationsUseCase, Clock clock,
                                      @Value("${reservation.orphan-grace-minutes:30}") long graceMinutes) {
        if (graceMinutes < 1) {
            // 0 would release a reservation the moment it is created, before contract-service can persist it
            throw new IllegalArgumentException("reservation.orphan-grace-minutes must be at least 1");
        }
        this.reapOrphanReservationsUseCase = reapOrphanReservationsUseCase;
        this.clock = clock;
        this.grace = Duration.ofMinutes(graceMinutes);
    }

    @Scheduled(fixedDelayString = "${reservation.orphan-reaper-check-ms:60000}")
    public void reapOrphanReservations() {
        int released = reapOrphanReservationsUseCase.execute(clock.instant().minus(grace));
        if (released > 0) {
            log.info("Orphan reservation reaper released {} reservation(s)", released);
        }
    }
}
