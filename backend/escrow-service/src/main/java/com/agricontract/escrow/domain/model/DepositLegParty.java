package com.agricontract.escrow.domain.model;

// Whose deposit a leg locks, with the bank.lock_requested wire values for it (golden-flow-events.yaml
// bankLockRequested: entryType, fundType). Mirrors chk_deposit_leg_leg.
public enum DepositLegParty {
    BUYER("LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT"),
    SELLER("LOCK_SELLER_DEPOSIT", "SELLER_DEPOSIT");

    private final String entryType;
    private final String fundType;

    DepositLegParty(String entryType, String fundType) {
        this.entryType = entryType;
        this.fundType = fundType;
    }

    public String entryType() {
        return entryType;
    }

    public String fundType() {
        return fundType;
    }
}
