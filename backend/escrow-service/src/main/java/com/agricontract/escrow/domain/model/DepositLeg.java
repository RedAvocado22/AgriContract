package com.agricontract.escrow.domain.model;

import lombok.Getter;

import java.time.Instant;

// One contract-level deposit to lock at activation (milestone-escrow design §2.3, §3.1). Escrow keeps the leg's
// state only; the money itself lives in bank-service ledger_entry. Table: deposit_leg (V9).
@Getter
public final class DepositLeg {

    private final String legId;
    private final String contractId;
    private final DepositLegParty party;
    private final String userId;            // buyerId or sellerId from contract.signed
    private final Money amount;             // buyerDepositAmount / sellerDepositAmount from contract.signed
    private DepositLegStatus status;
    private final String requestEventId;    // eventId = sourceEventId of bank.lock_requested; a retry resends it (D27)
    private final Instant createdAt;
    private Instant updatedAt;

    private DepositLeg(String legId, String contractId, DepositLegParty party, String userId, Money amount,
                       DepositLegStatus status, String requestEventId, Instant createdAt, Instant updatedAt) {
        this.legId = legId;
        this.contractId = contractId;
        this.party = party;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.requestEventId = requestEventId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * New leg in REQUESTED, with a fresh legId and requestEventId.
     * TODO(#101): guards (contractId/userId are UUIDs, party/amount/now present), then build the leg.
     */
    public static DepositLeg request(String contractId, DepositLegParty party, String userId, Money amount,
                                     Instant now) {
        throw new UnsupportedOperationException("TODO #101");
    }

    /** Rebuilds a stored leg; no validation, the row was valid when written. */
    public static DepositLeg reconstitute(String legId, String contractId, DepositLegParty party, String userId,
                                          Money amount, DepositLegStatus status, String requestEventId,
                                          Instant createdAt, Instant updatedAt) {
        return new DepositLeg(legId, contractId, party, userId, amount, status, requestEventId, createdAt, updatedAt);
    }
}
