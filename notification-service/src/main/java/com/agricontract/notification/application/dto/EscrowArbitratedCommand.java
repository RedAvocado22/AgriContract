package com.agricontract.notification.application.dto;

public record EscrowArbitratedCommand(
        String eventId,
        String escrowId,
        String contractId,
        String buyerEmail,
        String sellerEmail,
        String justification
) {
}
