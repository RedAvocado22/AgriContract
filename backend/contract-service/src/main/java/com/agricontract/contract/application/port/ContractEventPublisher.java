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

    /**
     * notification.contract_anchor_requested (#360): the "contract signed" email to both parties, with the signed
     * terms and their hash. Written next to contract.signed, which notification-service does not consume.
     */
    void contractAnchorRequested(Contract contract, Instant signedAt);
}
