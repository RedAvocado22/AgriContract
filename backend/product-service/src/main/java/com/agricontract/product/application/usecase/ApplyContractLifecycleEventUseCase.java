package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ContractLifecycleCommand;

public interface ApplyContractLifecycleEventUseCase {
    void execute(ContractLifecycleCommand command);
}
