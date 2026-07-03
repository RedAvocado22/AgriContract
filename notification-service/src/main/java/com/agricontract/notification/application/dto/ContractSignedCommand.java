package com.agricontract.notification.application.dto;

public record ContractSignedCommand(String eventId, String contractId, String buyerEmail, String sellerEmail) {}
