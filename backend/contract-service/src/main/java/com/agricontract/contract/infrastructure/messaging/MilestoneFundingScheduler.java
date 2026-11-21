package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.usecase.RequestDueMilestoneFundingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls for milestones whose funding date has come (D64). One failing milestone does not stop the next. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneFundingScheduler {
    private final RequestDueMilestoneFundingUseCase requestDue;

    @Scheduled(fixedDelayString = "${contract.milestone.funding-poll-interval:PT1M}")
    public void poll() {
        try {
            int requested = requestDue.execute();
            if (requested > 0) {
                log.info("event=milestone_funding_requests_written count={}", requested);
            }
        } catch (RuntimeException e) {
            log.error("event=milestone_funding_poll_failed reason={}", e.getMessage(), e);
        }
    }
}
