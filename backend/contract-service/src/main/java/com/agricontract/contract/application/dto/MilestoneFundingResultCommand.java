package com.agricontract.contract.application.dto;

import java.time.Instant;

/**
 * escrow.milestone_funded (lockedAt set, reason null) or escrow.milestone_funding_failed (reason set, lockedAt null),
 * parsed by the consumer.
 */
public record MilestoneFundingResultCommand(
        String eventId,
        String contractId,
        String milestoneId,
        Instant lockedAt,
        String reason) {
}
