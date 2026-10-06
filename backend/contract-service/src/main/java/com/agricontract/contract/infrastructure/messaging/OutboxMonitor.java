package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.infrastructure.persistence.repository.ContractDomainEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.agricontract.contract.infrastructure.persistence.entity.ContractDomainEventJpaEntity.Status.PENDING;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMonitor {
    private static final long STALE_THRESHOLD_MINUTES = 5;

    private final ContractDomainEventJpaRepository repository;

    @Scheduled(fixedDelay = 300000)
    public void checkStalePendingEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        long count = repository.countByStatusAndCreatedAtBefore(PENDING, threshold);
        if (count > 0) {
            log.warn("{} outbox event(s) stuck PENDING for over {} minute(s) — need manual check", count, STALE_THRESHOLD_MINUTES);
        }
    }
}
