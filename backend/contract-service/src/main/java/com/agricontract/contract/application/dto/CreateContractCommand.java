package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.vo.ContractTerms;

public record CreateContractCommand(
        String contractId,
        String buyerId,
        String listingId,
        ContractTerms terms
) {}
