package com.agricontract.contract.application.dto;

public record SignContractCommand(
        String contractId,
        String userId
) {}
