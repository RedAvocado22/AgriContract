package com.agricontract.product.common.exception;

/**
 * Persisted inventory contradicts a reservation (e.g. a restore would exceed the offered quantity).
 * Never a client error: surfaces as 500 and is logged for an operator to investigate.
 */
public class InventoryInvariantViolationException extends RuntimeException {
    public InventoryInvariantViolationException(String message) {
        super(message);
    }
}
