package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.LockResultCommand;

// bank.lock_completed / bank.lock_failed for a deposit leg (#102, D39). Runs in the consumer's transaction.
public interface RecordLockResultUseCase {

    void execute(LockResultCommand command);
}
