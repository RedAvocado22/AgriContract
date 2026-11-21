package com.agricontract.contract.domain.model.milestone;

/**
 * Delivery state of one milestone (milestone-escrow design §3.2). CREATED until the batch is funded
 * (escrow.milestone_funded, D64): the seller has no delivery obligation before IN_PROGRESS. The later states are
 * entered by the weigh, confirm, flag and force-majeure actions (#121 onward).
 */
public enum MilestoneStatus {
    CREATED,
    IN_PROGRESS,
    SELLER_WEIGHED,
    BUYER_RECEIVED,
    AWAITING_SELLER_RESPONSE,
    CONTESTED,
    FORCE_MAJEURE_PENDING_REVIEW,
    SETTLED
}
