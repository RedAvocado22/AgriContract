package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ContractCancelledEvent extends BaseNotificationEvent {
    private String contractId;
    private CancelledBy cancelledBy;
    private String reason;
    private BigDecimal buyerPenaltyRate;
}
