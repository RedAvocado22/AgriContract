package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

@Getter
public class EscrowPenalizedEvent extends BaseNotificationEvent {
    private String escrowId;
    private String contractId;
    private PenalizedParty penalizedParty;
    private MoneyDto penaltyAmount;
}
