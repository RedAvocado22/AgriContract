package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;


@Getter
public class EscrowReleasedEvent extends BaseNotificationEvent {
    private String escrowId;
    private String contractId;
}
