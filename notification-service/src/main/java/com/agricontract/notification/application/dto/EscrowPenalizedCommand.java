package com.agricontract.notification.application.dto;

public record EscrowPenalizedCommand(String eventId, String escrowId, String penalizedPartyEmail) {}
