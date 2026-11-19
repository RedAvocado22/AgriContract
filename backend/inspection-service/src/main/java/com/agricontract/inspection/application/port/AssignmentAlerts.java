package com.agricontract.inspection.application.port;

import com.agricontract.inspection.domain.model.WaitingAssignment;

public interface AssignmentAlerts {
    enum Stage { WAITING_STARTED, STILL_WAITING, ASSIGNED }
    void waitingChanged(WaitingAssignment waiting, Stage stage, String correlationId, String causationId);
}
