package com.agricontract.contract.domain.model.milestone;

/**
 * Funding projection of one milestone (design §6b, D64), kept apart from the delivery state.
 * <ul>
 *   <li>NOT_REQUESTED: before fundingScheduledDate; batchAmount is not fixed yet (a price adjustment can still
 *       change it).</li>
 *   <li>FUNDING_PENDING: milestone.funding_requested emitted, escrow is locking batchAmount.</li>
 *   <li>LOCKED: escrow.milestone_funded received.</li>
 *   <li>FUNDING_FAILED: escrow.milestone_funding_failed received; an OPERATOR retry (#239) can still lock it.</li>
 * </ul>
 */
public enum FundingStatus {
    NOT_REQUESTED,
    FUNDING_PENDING,
    LOCKED,
    FUNDING_FAILED
}
