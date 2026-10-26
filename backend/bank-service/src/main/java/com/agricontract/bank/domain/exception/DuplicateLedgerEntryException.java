package com.agricontract.bank.domain.exception;

// Another delivery of the same command won the insert race on source_event_id; the entry it wrote is the result.
public class DuplicateLedgerEntryException extends RuntimeException {

    public DuplicateLedgerEntryException(String sourceEventId) {
        super("Ledger entry already recorded for sourceEventId " + sourceEventId);
    }
}
