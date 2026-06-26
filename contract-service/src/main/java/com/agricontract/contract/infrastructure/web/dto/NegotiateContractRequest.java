package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.domain.model.vo.ContractTerms;

public record NegotiateContractRequest(ContractTerms newTerms) {}
