package com.agricontract.product.infrastructure.messaging;

import com.agricontract.product.infrastructure.persistence.entity.ProductDomainEventJpaEntity;
import com.agricontract.product.infrastructure.persistence.repository.ProductDomainEventJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static com.agricontract.product.infrastructure.persistence.entity.ProductDomainEventJpaEntity.Status.PENDING;
import static com.agricontract.product.infrastructure.persistence.entity.ProductDomainEventJpaEntity.Status.PUBLISHED;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private static final String EXCHANGE = "agricontract.events";

    private final ProductDomainEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        for (ProductDomainEventJpaEntity row : repository.findByStatusOrderByCreatedAtAsc(PENDING)) {
            try {
                Map<String, Object> payload = objectMapper.readValue(row.getPayload(), new TypeReference<>() {});
                rabbitTemplate.convertAndSend(EXCHANGE, row.getEventType(), payload);
                row.setStatus(PUBLISHED);
                row.setPublishedAt(LocalDateTime.now());
                repository.save(row);
                log.debug("Published event {} ({}) to {}", row.getId(), row.getEventType(), EXCHANGE);
            } catch (AmqpException e) {
                log.warn("Failed to publish event {} ({}) to broker, will retry next poll: {}", row.getId(), row.getEventType(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error processing event {} ({}): {}", row.getId(), row.getEventType(), e.getMessage(), e);
            }
        }
    }
}
