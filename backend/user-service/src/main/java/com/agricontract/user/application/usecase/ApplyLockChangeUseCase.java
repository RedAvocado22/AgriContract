package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.dto.LockChangeOutcome;

public interface ApplyLockChangeUseCase {

    LockChangeOutcome execute(ApplyLockChangeCommand command);
}
