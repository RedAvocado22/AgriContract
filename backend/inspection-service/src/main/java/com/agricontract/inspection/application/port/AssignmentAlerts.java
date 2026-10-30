package com.agricontract.inspection.application.port;

public interface AssignmentAlerts {
    void emptyPool(String contractId, String milestoneId, String sourceId, boolean replacement);
}
