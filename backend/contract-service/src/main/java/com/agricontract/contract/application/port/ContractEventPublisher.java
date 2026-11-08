package com.agricontract.contract.application.port;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.activation.ActivationRecovery;
import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.util.List;

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

    /**
     * contract.activated (#103): product-service's hard inventory commit. correlationId continues the activation
     * chain; causationId is the escrow.deposit_locked that activated the contract.
     */
    void contractActivated(Contract contract, Instant activatedAt, String correlationId, String causationId);

    /**
     * notification.contract_activation_failed_requested (#234, D42): both parties learn that the deposits could not be
     * locked, what they can do next and from when one party's cancel request is enough.
     */
    void contractActivationFailedRequested(Contract contract, ActivationRecovery recovery, String buyerDepositState,
                                           String sellerDepositState, String correlationId, String causationId);

    /** contract.deposit_lock_retry_requested (D42): OPERATOR asks escrow to lock the failed legs again. */
    void depositLockRetryRequested(Contract contract, String operatorUserId, Instant requestedAt);

    /**
     * contract.activation_refund_requested (D42), with SIGNED → ACTIVATION_REFUND_PENDING: escrow returns the locked
     * deposits. trigger is BOTH_PARTIES or ONE_PARTY_AFTER_WINDOW.
     */
    void activationRefundRequested(Contract contract, List<SignerRole> requestedBy, String trigger, Instant at);

    /** notification.activation_cancel_requested (D42): the other party learns of the request and of the window. */
    void activationCancelRequested(Contract contract, SignerRole requestedBy, Instant oneSidedCancelAllowedFrom,
                                   Instant at);

    /** contract.activation_failed: terminal ACTIVATION_FAILED, product-service releases the reservation. */
    void contractActivationFailed(Contract contract, Instant failedAt, String correlationId, String causationId);
}
