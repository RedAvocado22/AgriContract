package com.agricontract.contract.application.dto;

public record ConfirmDeliveryCommand(
        String contractId,
        String buyerId
) {}
