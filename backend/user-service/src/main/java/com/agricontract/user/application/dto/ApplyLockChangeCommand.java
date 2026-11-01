package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.LockAction;

import java.time.Instant;

/**
 * One reputation.locked/unlocked event, already checked against the frozen schema.
 * until is lockedUntil for LOCKED (never null) and effectiveLockedUntil for UNLOCKED (null = no lock left).
 * sourceEventId and correlationId carry the event's chain into the notification command.
 */
public record ApplyLockChangeCommand(
        String sourceEventId,
        String correlationId,
        String userId,
        LockAction action,
        Instant until,
        String reasonCode,
        long lockRevision
) {
    public ApplyLockChangeCommand {
        if (action == LockAction.LOCKED && until == null) {
            throw new IllegalArgumentException("lockedUntil is required for a lock");
        }
        if (lockRevision < 1) {
            throw new IllegalArgumentException("lockRevision must be >= 1");
        }
    }
}
