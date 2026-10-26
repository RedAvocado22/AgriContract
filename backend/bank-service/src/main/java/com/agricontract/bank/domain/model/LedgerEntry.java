package com.agricontract.bank.domain.model;

import com.agricontract.bank.domain.exception.InvalidLedgerEntryException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// One append-only ledger row (bank-service-phase2-design.md §2, §6). Never updated or deleted.
@Getter
public final class LedgerEntry {

    private final String entryId;
    private final String sourceEventId;     // idempotency key (§4)
    private final String contractId;
    private final String milestoneId;       // null = contract-level deposit
    private final String userId;
    private final EntryType entryType;
    private final FundType fundType;
    private final String remedyDecisionId;  // null outside remedy legs
    private final String remedyLegId;       // null outside remedy legs; unique per leg
    private final Money amount;
    private final Instant createdAt;

    private LedgerEntry(String entryId, String sourceEventId, String contractId, String milestoneId,
                        String userId, EntryType entryType, FundType fundType,
                        String remedyDecisionId, String remedyLegId, Money amount, Instant createdAt) {
        this.entryId = entryId;
        this.sourceEventId = sourceEventId;
        this.contractId = contractId;
        this.milestoneId = milestoneId;
        this.userId = userId;
        this.entryType = entryType;
        this.fundType = fundType;
        this.remedyDecisionId = remedyDecisionId;
        this.remedyLegId = remedyLegId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    /**
     * New entry from an already-computed amount. Checks only the shape of one row (bank design §2b): conservation
     * across rows (no withdrawing more than is locked) is escrow-service's job before it sends the command.
     */
    public static LedgerEntry create(String sourceEventId, String contractId, String milestoneId, String userId,
                                     EntryType entryType, FundType fundType,
                                     String remedyDecisionId, String remedyLegId, Money amount, Instant now) {
        requireUuid("sourceEventId", sourceEventId);
        requireUuid("contractId", contractId);
        requireUuid("userId", userId);
        if (milestoneId != null) requireUuid("milestoneId", milestoneId);
        if (remedyDecisionId != null) requireUuid("remedyDecisionId", remedyDecisionId);
        if (remedyLegId != null) requireUuid("remedyLegId", remedyLegId);
        if (entryType == null || fundType == null || amount == null || now == null) {
            throw new InvalidLedgerEntryException("entryType, fundType, amount and timestamp are required");
        }

        if (!entryType.allows(fundType)) {
            throw new InvalidLedgerEntryException(entryType + " cannot move " + fundType);
        }
        // Deposits are contract-level, a batch belongs to exactly one milestone.
        if ((fundType == FundType.MILESTONE_PAYMENT) != (milestoneId != null)) {
            throw new InvalidLedgerEntryException(fundType == FundType.MILESTONE_PAYMENT
                    ? "MILESTONE_PAYMENT needs a milestoneId" : fundType + " is contract-level and takes no milestoneId");
        }
        if ((remedyDecisionId == null) != (remedyLegId == null)) {
            throw new InvalidLedgerEntryException("remedyDecisionId and remedyLegId come together");
        }
        boolean linked = remedyLegId != null;
        switch (entryType.remedyLink()) {
            case FORBIDDEN -> {
                if (linked) throw new InvalidLedgerEntryException(entryType + " is never a remedy leg");
            }
            case REQUIRED -> {
                if (!linked) throw new InvalidLedgerEntryException(entryType + " needs a remedy decision");
            }
            case OPTIONAL -> { }
        }

        return new LedgerEntry(UUID.randomUUID().toString(), sourceEventId, contractId, milestoneId, userId,
                entryType, fundType, remedyDecisionId, remedyLegId, amount, now);
    }

    // Loading a row that is already in the ledger; no validation re-run.
    public static LedgerEntry reconstitute(String entryId, String sourceEventId, String contractId, String milestoneId,
                                           String userId, EntryType entryType, FundType fundType,
                                           String remedyDecisionId, String remedyLegId, Money amount,
                                           Instant createdAt) {
        return new LedgerEntry(entryId, sourceEventId, contractId, milestoneId, userId, entryType, fundType,
                remedyDecisionId, remedyLegId, amount, createdAt);
    }

    private static void requireUuid(String field, String value) {
        if (value == null) {
            throw new InvalidLedgerEntryException(field + " is required");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value.toLowerCase())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidLedgerEntryException(field + " is not a UUID: " + value);
        }
    }
}
