package com.agricontract.contract.domain.event;

import java.time.LocalDateTime;

public interface DomainEvent {
    String getEventId();
    String getEventType();
    String getAggregateId();
    LocalDateTime getOccurredAt();
}
