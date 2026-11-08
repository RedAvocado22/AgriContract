package com.agricontract.escrow.domain.model;

// Whose deposit a leg locks, with the bank.lock_requested wire values for it (golden-flow-events.yaml
// bankLockRequested: entryType, fundType). Mirrors chk_deposit_leg_leg.
public enum DepositLegParty {
    // Return on cancel (#247): technical failure → every deposit back to its owner by fundType (milestone-escrow §6.7).
    BUYER("LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT", "bank.refund_requested", "REFUND_TO_BUYER"),
    SELLER("LOCK_SELLER_DEPOSIT", "SELLER_DEPOSIT", "bank.release_requested", "RELEASE_TO_SELLER");

    private final String entryType;
    private final String fundType;
    private final String returnCommand;
    private final String returnEntryType;

    DepositLegParty(String entryType, String fundType, String returnCommand, String returnEntryType) {
        this.entryType = entryType;
        this.fundType = fundType;
        this.returnCommand = returnCommand;
        this.returnEntryType = returnEntryType;
    }

    /** The bank command that gives this deposit back to its owner. */
    public String returnCommand() {
        return returnCommand;
    }

    public String returnEntryType() {
        return returnEntryType;
    }

    public String entryType() {
        return entryType;
    }

    public String fundType() {
        return fundType;
    }
}
