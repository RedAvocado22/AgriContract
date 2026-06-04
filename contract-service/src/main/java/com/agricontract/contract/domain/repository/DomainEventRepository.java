package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.event.DomainEvent;

import java.util.List;

/**
 * Outbox table port — events written here in the same DB transaction as the aggregate.
 * A scheduler reads PENDING events and publishes them to RabbitMQ.
 */
public interface DomainEventRepository {
    void saveAll(List<DomainEvent> events);
    List<DomainEvent> findPending(int batchSize);
    void markPublished(String eventId);
    void markFailed(String eventId);
}
