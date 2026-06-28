package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

@Getter
public class ContractDeliveredEvent extends BaseNotificationEvent {
    private String contractId;
    private String confirmedBy;
}
