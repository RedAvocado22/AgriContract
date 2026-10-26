package com.agricontract.bank.domain.model;

import java.util.EnumSet;
import java.util.Set;

import static com.agricontract.bank.domain.model.FundType.BUYER_DEPOSIT;
import static com.agricontract.bank.domain.model.FundType.MILESTONE_PAYMENT;
import static com.agricontract.bank.domain.model.FundType.SELLER_DEPOSIT;

// bank-service-phase2-design.md §2, §6.7 (milestone-escrow). Mirrors chk_ledger_entry_type, chk_ledger_entry_fund and
// chk_ledger_remedy_link in V1__create_ledger_entry.sql: keep both in step.
public enum EntryType {
    LOCK_BUYER_DEPOSIT(EnumSet.of(BUYER_DEPOSIT), RemedyLink.FORBIDDEN),
    LOCK_SELLER_DEPOSIT(EnumSet.of(SELLER_DEPOSIT), RemedyLink.FORBIDDEN),
    LOCK_MILESTONE(EnumSet.of(MILESTONE_PAYMENT), RemedyLink.FORBIDDEN),
    // Sale proceeds, or the seller's own deposit going back.
    RELEASE_TO_SELLER(EnumSet.of(MILESTONE_PAYMENT, SELLER_DEPOSIT), RemedyLink.OPTIONAL),
    REFUND_TO_BUYER(EnumSet.of(MILESTONE_PAYMENT, BUYER_DEPOSIT), RemedyLink.OPTIONAL),
    // Money consequences exist only through remedy.finalized, so these always point at a decision.
    DEPOSIT_FORFEITURE(EnumSet.of(BUYER_DEPOSIT, SELLER_DEPOSIT), RemedyLink.REQUIRED),
    CONTRACTUAL_PENALTY(EnumSet.of(MILESTONE_PAYMENT), RemedyLink.REQUIRED),
    DAMAGES_COMPENSATION(EnumSet.of(MILESTONE_PAYMENT), RemedyLink.REQUIRED);

    public enum RemedyLink { FORBIDDEN, OPTIONAL, REQUIRED }

    private final Set<FundType> allowedFunds;
    private final RemedyLink remedyLink;

    EntryType(Set<FundType> allowedFunds, RemedyLink remedyLink) {
        this.allowedFunds = allowedFunds;
        this.remedyLink = remedyLink;
    }

    public boolean allows(FundType fundType) {
        return allowedFunds.contains(fundType);
    }

    public RemedyLink remedyLink() {
        return remedyLink;
    }
}
