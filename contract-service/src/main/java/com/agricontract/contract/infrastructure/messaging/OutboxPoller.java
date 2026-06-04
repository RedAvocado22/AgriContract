package com.agricontract.contract.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Outbox Pattern poller: reads PENDING events from contract_domain_events → publishes to RabbitMQ → marks PUBLISHED
// Phase 1: @Scheduled polling every 1s. Phase 2: upgrade to Debezium CDC.
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}")
    public void poll() {
        // TODO: SELECT PENDING → publish to RabbitMQ → UPDATE PUBLISHED
    }
}
