package com.agricontract.escrow.application.port;

import com.agricontract.escrow.domain.model.DepositLeg;

// Writes bank.lock_requested for a leg to the outbox. Must run inside the caller's transaction, so a command is
// never sent for a leg that rolled back.
public interface LockCommandPublisher {

    void lockRequested(DepositLeg leg, String correlationId, String causationId);
}
