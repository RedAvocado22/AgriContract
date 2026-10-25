package com.agricontract.inspection.infrastructure.scheduling;

import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import com.agricontract.inspection.domain.repository.AssignmentLifecycleRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inspection.assignment.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AssignmentTimeoutJob {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentTimeoutJob.class);
    private final AssignmentLifecycleRepository assignments;
    private final ManageLevel15Assignments lifecycle;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${inspection.assignment.scan-interval-ms:60000}")
    public void scan() {
        for (var assignment : assignments.overdue(clock.instant(), 100)) {
            try { lifecycle.timeout(assignment.commissionId()); }
            catch (RuntimeException failure) {
                LOG.warn("inspection_assignment_timeout_failed audience=OPERATOR commissionId={}", assignment.commissionId());
            }
        }
    }
}
