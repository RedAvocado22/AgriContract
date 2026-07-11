package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.vo.ContractTerms;

public record NegotiateContractCommand(
        String contractId,
        String userId,
        ContractTerms newTerms
) {}
