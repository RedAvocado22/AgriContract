package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.inspection.application.port.AssignmentAlerts;
import com.agricontract.inspection.domain.model.WaitingAssignment;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class OutboxAssignmentAlerts implements AssignmentAlerts {
    public static final String EVENT_TYPE = "notification.level1_5_assignment_waiting_requested";
    private static final Logger LOG = LoggerFactory.getLogger(OutboxAssignmentAlerts.class);
    private final OutboxWriter outbox;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void waitingChanged(WaitingAssignment waiting, Stage stage) {
        UUID eventId = UUID.randomUUID();
        var payload = Map.<String, Object>of("contractId", waiting.contractId(), "milestoneId", waiting.milestoneId(),
                "reason", waiting.reason().name(), "waitingSince", waiting.waitingSince().toString(), "stage", stage.name());
        outbox.append(new EventEnvelope(eventId, EVENT_TYPE, 1, clock.instant(), "inspection-service",
                waiting.milestoneId(), waiting.milestoneId(), null, null, payload));
        // Preserve operational visibility after commit only; email delivery belongs to notification-service.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                LOG.warn("inspection_assignment_waiting audience=OPERATOR contractId={} milestoneId={} reason={} stage={}",
                        waiting.contractId(), waiting.milestoneId(), waiting.reason(), stage);
            }
        });
    }
}
