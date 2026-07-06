package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.domain.model.vo.ContractTerms;
import jakarta.validation.constraints.NotNull;

public record NegotiateContractRequest(@NotNull ContractTerms newTerms) {}
