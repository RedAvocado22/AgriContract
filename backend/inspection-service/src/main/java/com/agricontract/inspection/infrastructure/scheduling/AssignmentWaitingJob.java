package com.agricontract.inspection.infrastructure.scheduling;

import com.agricontract.inspection.application.port.AssignmentWaitingRepository;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import com.agricontract.inspection.domain.model.WaitingAssignment.Key;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inspection.assignment.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AssignmentWaitingJob {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentWaitingJob.class);
    private static final int BATCH_SIZE = 100;
    private final AssignmentWaitingRepository waiting;
    private final ManageLevel15Assignments lifecycle;

    @Scheduled(fixedDelayString = "${inspection.assignment.waiting-retry-interval-ms:3600000}")
    public void scan() {
        Key after = null;
        while (true) {
            var page = waiting.pendingAfter(after, BATCH_SIZE);
            for (Key key : page) {
                try {
                    lifecycle.retryWaiting(key);
                } catch (RuntimeException failure) {
                    // The use case transaction has rolled back; continue with other pending requests.
                    LOG.warn("inspection_assignment_waiting_retry_failed contractId={} milestoneId={} failure={}",
                            key.contractId(), key.milestoneId(), failure.getClass().getSimpleName());
                }
            }
            if (page.size() < BATCH_SIZE) {
                return;
            }
            after = page.getLast();
        }
    }
}
