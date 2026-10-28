package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ContractSignedCommand;

public interface RequestActivationDepositsUseCase {

    /** Runs inside the caller's transaction, after the caller has claimed the contract.signed eventId. */
    void execute(ContractSignedCommand command);
}
