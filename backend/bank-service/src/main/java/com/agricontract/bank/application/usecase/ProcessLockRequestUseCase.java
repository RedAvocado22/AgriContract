package com.agricontract.bank.application.usecase;

import com.agricontract.bank.application.dto.LockFundsCommand;

public interface ProcessLockRequestUseCase {

    void execute(LockFundsCommand command);
}
