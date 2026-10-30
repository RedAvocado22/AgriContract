package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ContractCreatedCommand;

public interface ConfirmContractCreatedUseCase {
    void execute(ContractCreatedCommand command);
}
