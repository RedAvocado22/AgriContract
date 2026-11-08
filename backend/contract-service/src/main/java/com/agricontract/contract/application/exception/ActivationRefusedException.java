package com.agricontract.contract.application.exception;

/**
 * escrow.deposit_locked that cannot activate its contract (unknown contract, not SIGNED, deposit states that do
 * not match the terms). Non-retryable: the message goes to the DLQ so an operator sees money locked for a contract
 * that did not become ACTIVE (D40). Never dropped silently.
 */
public class ActivationRefusedException extends RuntimeException {
    public ActivationRefusedException(String message) {
        super(message);
    }
}
