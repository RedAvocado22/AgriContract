package com.agricontract.contract.application.dto;

public record CancelContractCommand(
        String contractId,
        String userId,
        String reason
) {}
