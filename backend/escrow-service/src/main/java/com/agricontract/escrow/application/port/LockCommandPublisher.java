package com.agricontract.escrow.application.port;

import com.agricontract.escrow.domain.model.DepositLeg;

// Writes bank.lock_requested for a leg to the outbox. Must run inside the caller's transaction, so a command is
// never sent for a leg that rolled back.
public interface LockCommandPublisher {

    void lockRequested(DepositLeg leg, String correlationId, String causationId);

    /**
     * Sends the leg's original bank.lock_requested again, same eventId and payload (bank dedups on sourceEventId and
     * re-evaluates a request it refused, D39). Must run inside the caller's transaction.
     */
    void resend(DepositLeg leg);
}
