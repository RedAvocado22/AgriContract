package com.agricontract.contract.application.exception;

/**
 * An escrow funding result that cannot apply to its milestone: unknown milestone, a contract that does not match, or a
 * funding state that does not allow it. Non-retryable: the message goes to the DLQ so an operator sees money whose
 * milestone did not follow. Never dropped silently.
 */
public class MilestoneFundingRefusedException extends RuntimeException {
    public MilestoneFundingRefusedException(String message) {
        super(message);
    }

    public MilestoneFundingRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
