package com.agricontract.notification.application.dto;

public record ContractDeliveredCommand(String eventId, String contractId, String sellerEmail) {}
