package com.agricontract.user.application.port;

import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.domain.model.UserProfile;

// Writes notification.user_lock_changed_requested to the outbox. Must run inside the transaction that applied the
// change, so a rolled-back or redelivered event never requests a second email.
public interface LockChangePublisher {

    // profile is read after the change: its lockedUntil is the effective lock the email reports.
    void lockChanged(UserProfile profile, ApplyLockChangeCommand change);
}
