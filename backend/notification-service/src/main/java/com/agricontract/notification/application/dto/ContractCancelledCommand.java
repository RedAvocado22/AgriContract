package com.agricontract.notification.application.dto;

public record ContractCancelledCommand(String eventId, String contractId, String buyerEmail, String sellerEmail, String reason) {}
