package com.agricontract.notification.application.dto;

public record EscrowLockedCommand(String eventId, String escrowId, String sellerEmail) {}
