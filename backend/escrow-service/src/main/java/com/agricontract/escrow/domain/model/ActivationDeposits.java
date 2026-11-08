package com.agricontract.escrow.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * The deposit legs of one contract's activation, read together under the contract's row lock (#102, #247).
 * The set ends in one of two ways: every leg LOCKED (escrow.deposit_locked), or nothing left in progress while at
 * least one leg's lock failed with no retry left (escrow.deposit_lock_failed). A leg in progress is REQUESTED, or
 * FAILED with a retry due.
 */
public record ActivationDeposits(List<DepositLeg> legs) {

    /** Which legs failed, as golden-flow-events.yaml escrowDepositLockFailed.failedLeg. */
    public enum FailedLeg { BUYER, SELLER, BOTH }

    public ActivationDeposits {
        legs = List.copyOf(legs);
    }

    public boolean allLocked() {
        return !legs.isEmpty() && legs.stream().allMatch(DepositLeg::isLocked);
    }

    /** Nothing left in progress, and the lock failed for at least one leg. */
    public boolean lockFailed() {
        return legs.stream().anyMatch(DepositLeg::isLockFailed)
                && legs.stream().allMatch(l -> l.isLocked() || l.isLockFailed());
    }

    /** The parties cancelled and every leg is settled: never locked (CANCELLED) or given back (RETURNED). */
    public boolean refundSettled() {
        return !legs.isEmpty() && legs.stream().allMatch(DepositLeg::isRefundSettled);
    }

    public FailedLeg failedLeg() {
        boolean buyer = failed(DepositLegParty.BUYER);
        boolean seller = failed(DepositLegParty.SELLER);
        if (buyer && seller) {
            return FailedLeg.BOTH;
        }
        if (!buyer && !seller) {
            throw new IllegalStateException("No deposit leg failed");
        }
        return buyer ? FailedLeg.BUYER : FailedLeg.SELLER;
    }

    public Optional<DepositLeg> leg(DepositLegParty party) {
        return legs.stream().filter(l -> l.getParty() == party).findFirst();
    }

    private boolean failed(DepositLegParty party) {
        return leg(party).map(DepositLeg::isLockFailed).orElse(false);
    }
}
