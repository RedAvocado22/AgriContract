package com.agricontract.product.infrastructure.scheduling;

import com.agricontract.product.domain.repository.PlotRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Geo check timeout (design §2.2.4): a plot still PENDING after geoVerificationTimeoutHours becomes UNVERIFIED
 * ("not known", not "risky") and can then back a listing. Until the forest-baseline check exists, this is the only
 * way a plot leaves PENDING.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoVerificationTimeoutJob {

    private final PlotRegistryRepository plotRegistryRepository;
    private final Clock clock;

    @Value("${plot.geo-verification-timeout-hours:6}")
    private long timeoutHours;

    @Scheduled(fixedDelayString = "${plot.geo-verification-timeout-check-ms:600000}")
    public void markTimedOutPlotsUnverified() {
        int moved = plotRegistryRepository.markUnverifiedImportedBefore(
                clock.instant().minus(Duration.ofHours(timeoutHours)));
        if (moved > 0) {
            log.info("Geo verification timed out for {} plot(s): PENDING -> UNVERIFIED", moved);
        }
    }
}
