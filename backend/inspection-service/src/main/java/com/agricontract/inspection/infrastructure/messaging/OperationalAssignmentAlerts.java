package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.inspection.application.port.AssignmentAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OperationalAssignmentAlerts implements AssignmentAlerts {
    private static final Logger LOG = LoggerFactory.getLogger(OperationalAssignmentAlerts.class);

    @Override
    public void emptyPool(String contractId, String milestoneId, String sourceId, boolean replacement) {
        // Match existing structured operational monitors; no invented notification protocol.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                LOG.warn("inspection_assignment_empty_pool audience=OPERATOR contractId={} milestoneId={} sourceId={} replacement={}",
                        contractId, milestoneId, sourceId, replacement);
            }
        });
    }
}
