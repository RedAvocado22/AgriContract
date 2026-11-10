package com.agricontract.product.application.exception;

/**
 * A lifecycle event that arrived before the one it follows (contract.activated before contract.signed: separate
 * queues give no order between them). Retried by the listener; nothing is recorded, so the redelivery is processed.
 */
public class ReservationNotReadyException extends RuntimeException {

    public ReservationNotReadyException(String message) {
        super(message);
    }
}
