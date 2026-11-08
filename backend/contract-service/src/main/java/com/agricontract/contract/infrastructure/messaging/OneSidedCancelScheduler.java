package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.usecase.StartDueOneSidedCancelsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls for one-sided cancel requests whose window has passed (D42). One failing contract does not stop the next. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneSidedCancelScheduler {
    private final StartDueOneSidedCancelsUseCase startDue;

    @Scheduled(fixedDelayString = "${contract.activation.one-sided-cancel-poll-interval:PT1M}")
    public void poll() {
        try {
            int started = startDue.execute();
            if (started > 0) {
                log.info("event=one_sided_cancels_started count={}", started);
            }
        } catch (RuntimeException e) {
            log.error("event=one_sided_cancel_poll_failed reason={}", e.getMessage(), e);
        }
    }
}
