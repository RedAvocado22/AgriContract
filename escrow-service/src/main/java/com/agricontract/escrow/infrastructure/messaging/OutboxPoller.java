package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.infrastructure.persistence.entity.EscrowDomainEventJpaEntity;
import com.agricontract.escrow.infrastructure.persistence.repository.EscrowDomainEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.agricontract.escrow.infrastructure.persistence.entity.EscrowDomainEventJpaEntity.Status.PENDING;
import static com.agricontract.escrow.infrastructure.persistence.entity.EscrowDomainEventJpaEntity.Status.PUBLISHED;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private static final String EXCHANGE = "agricontract.escrow";

    private final EscrowDomainEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        for (EscrowDomainEventJpaEntity row : repository.findByStatusOrderByCreatedAtAsc(PENDING)) {
            try {
                rabbitTemplate.convertAndSend(EXCHANGE, row.getEventType(), row.getPayload());
                row.setStatus(PUBLISHED);
                row.setPublishedAt(LocalDateTime.now());
                repository.save(row);
            } catch (AmqpException e) {
                log.error("Event {} with type: {} is {}", row.getId(), row.getEventType(), e.getMessage());
            }
        }
    }
}
