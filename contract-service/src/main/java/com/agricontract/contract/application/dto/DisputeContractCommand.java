package com.agricontract.contract.application.dto;

public record DisputeContractCommand(
        String contractId,
        String buyerId,
        String reason
) {}
