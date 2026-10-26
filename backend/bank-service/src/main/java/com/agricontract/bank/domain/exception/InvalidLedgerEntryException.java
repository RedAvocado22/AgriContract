package com.agricontract.bank.domain.exception;

// A ledger entry (or its amount) that must never reach the ledger. Payload bug on the sender side, not an
// infrastructure failure: callers treat it as non-retryable.
public class InvalidLedgerEntryException extends RuntimeException {

    public InvalidLedgerEntryException(String message) {
        super(message);
    }
}
