package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.infrastructure.persistence.entity.ContractDomainEventJpaEntity;
import com.agricontract.contract.infrastructure.persistence.repository.ContractDomainEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.agricontract.contract.infrastructure.persistence.entity.ContractDomainEventJpaEntity.Status.PENDING;
import static com.agricontract.contract.infrastructure.persistence.entity.ContractDomainEventJpaEntity.Status.PUBLISHED;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private static final String EXCHANGE = "agricontract.contracts";

    private final ContractDomainEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        for (ContractDomainEventJpaEntity row : repository.findByStatusOrderByCreatedAtAsc(PENDING)) {
            try {
                rabbitTemplate.convertAndSend(EXCHANGE, row.getEventType(), row.getPayload());
                row.setStatus(PUBLISHED);
                row.setPublishedAt(LocalDateTime.now());
                repository.save(row);
                log.debug("Published event {} ({}) to {}", row.getId(), row.getEventType(), EXCHANGE);
            } catch (AmqpException e) {
                log.error("Event {} with type: {} is {}", row.getId(), row.getEventType(), e.getMessage());
            }
        }
    }
}
