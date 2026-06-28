package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

@Getter
public class ContractDisputedEvent extends BaseNotificationEvent {
    private String contractId;
    private String reason;
    private String disputedBy;
}
