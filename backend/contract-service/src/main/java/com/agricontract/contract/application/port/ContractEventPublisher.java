package com.agricontract.contract.application.port;

import com.agricontract.contract.domain.model.Contract;

import java.time.Instant;

/**
 * Canonical contract events (golden-flow-events.yaml), written to the event outbox. Must be called inside the
 * transaction that makes the change, so the event exists exactly when the change commits.
 */
public interface ContractEventPublisher {

    /** contract.signed, once, by the verify that made the contract SIGNED; signedAt is the second signature. */
    void contractSigned(Contract contract, Instant signedAt);
}
