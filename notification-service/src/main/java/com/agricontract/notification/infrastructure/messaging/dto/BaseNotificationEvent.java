package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

import java.time.Instant;

@Getter
public class BaseNotificationEvent {
    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private String buyerEmail;
    private String sellerEmail;
}
