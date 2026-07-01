package com.agricontract.notification.application.dto;

public record EscrowReleasedCommand(String eventId, String escrowId, String buyerEmail, String sellerEmail) {}
