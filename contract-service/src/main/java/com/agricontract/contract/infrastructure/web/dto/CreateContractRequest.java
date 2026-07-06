package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.domain.model.vo.ContractTerms;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateContractRequest(
        @NotBlank String contractId,
        @NotBlank String listingId,
        @NotNull ContractTerms terms
) {}
