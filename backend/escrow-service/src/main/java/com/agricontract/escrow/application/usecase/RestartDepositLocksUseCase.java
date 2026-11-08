package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.DepositLockRetryCommand;

public interface RestartDepositLocksUseCase {

    /** Starts a new retry cycle for every leg of the contract whose lock failed; returns how many were resent. */
    int execute(DepositLockRetryCommand command);
}
