package com.agricontract.bank.application.dto;

import com.agricontract.bank.domain.model.EntryType;

import java.util.Arrays;
import java.util.Set;

/**
 * The bank commands escrow sends, by the entry types each may carry (golden-flow-events.yaml bank*Requested). The
 * entry type decides the operation, so a command needs no separate field. Seize/penalty legs (#138) add theirs later.
 */
public enum LedgerOperation {
    LOCK("bank.lock_requested", "bank.lock_completed", "bank.lock_failed",
            Set.of(EntryType.LOCK_BUYER_DEPOSIT, EntryType.LOCK_SELLER_DEPOSIT, EntryType.LOCK_MILESTONE)),
    RELEASE("bank.release_requested", "bank.release_completed", "bank.release_failed",
            Set.of(EntryType.RELEASE_TO_SELLER)),
    REFUND("bank.refund_requested", "bank.refund_completed", "bank.refund_failed",
            Set.of(EntryType.REFUND_TO_BUYER));

    private final String requested;
    private final String completed;
    private final String failed;
    private final Set<EntryType> entryTypes;

    LedgerOperation(String requested, String completed, String failed, Set<EntryType> entryTypes) {
        this.requested = requested;
        this.completed = completed;
        this.failed = failed;
        this.entryTypes = entryTypes;
    }

    public static LedgerOperation of(EntryType entryType) {
        return Arrays.stream(values()).filter(op -> op.entryTypes.contains(entryType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No bank command carries " + entryType));
    }

    public boolean allows(EntryType entryType) {
        return entryTypes.contains(entryType);
    }

    public String requested() { return requested; }
    public String completed() { return completed; }
    public String failed() { return failed; }
}
