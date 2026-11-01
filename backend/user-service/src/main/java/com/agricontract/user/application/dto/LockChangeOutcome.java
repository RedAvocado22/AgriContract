package com.agricontract.user.application.dto;

public enum LockChangeOutcome {
    // Projection updated and notification.user_lock_changed_requested written to the outbox.
    APPLIED,
    // lockRevision not greater than the stored one: an older or replayed decision, nothing changes.
    STALE,
    // No profile for userId: nothing to lock, nothing to notify.
    UNKNOWN_USER
}
