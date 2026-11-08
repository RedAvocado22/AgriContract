package com.agricontract.bank.application.usecase;

import com.agricontract.bank.application.dto.LedgerCommand;

public interface ProcessLedgerCommandUseCase {

    void execute(LedgerCommand command);
}
