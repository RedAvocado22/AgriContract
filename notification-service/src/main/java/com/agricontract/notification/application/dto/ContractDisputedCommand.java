package com.agricontract.notification.application.dto;

public record ContractDisputedCommand(String eventId, String contractId, String buyerEmail, String sellerEmail, String reason) {}
