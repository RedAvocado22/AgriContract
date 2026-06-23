package com.agricontract.escrow.infrastructure.persistence.repository;

import com.agricontract.escrow.domain.event.DomainEvent;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import com.agricontract.escrow.infrastructure.persistence.entity.EscrowAccountJpaEntity;
import com.agricontract.escrow.infrastructure.persistence.entity.EscrowDomainEventJpaEntity;
import com.agricontract.escrow.infrastructure.persistence.entity.EscrowTransactionJpaEntity;
import com.agricontract.escrow.infrastructure.persistence.mapper.EscrowMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EscrowAccountRepositoryImpl implements EscrowAccountRepository {

    private final EscrowAccountJpaRepository jpaRepo;
    private final EscrowMapper mapper;
    private final ObjectMapper objectMapper;
    private final EscrowDomainEventJpaRepository escrowDomainEventJpaRepository;

    @Override
    @Transactional
    public EscrowAccount save(EscrowAccount account) {
        EscrowAccountJpaEntity entity = mapper.toJpaEntity(account);

        jpaRepo.findByContractId(account.getContractId()).ifPresent(existing -> {
            entity.setId(existing.getId());

            Map<String, Long> existingTransactionIds = existing.getTransactions().stream()
                    .collect(Collectors.toMap(
                            EscrowTransactionJpaEntity::getTransactionId,
                            EscrowTransactionJpaEntity::getId));

            entity.getTransactions().forEach(tx ->
                    Optional.ofNullable(existingTransactionIds.get(tx.getTransactionId()))
                            .ifPresent(tx::setId));
        });

        EscrowAccountJpaEntity saved = jpaRepo.save(entity);

        for (DomainEvent domainEvent : account.pullDomainEvents()) {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(domainEvent);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize domain event " + domainEvent.getEventId(), e);
            }

            EscrowDomainEventJpaEntity event = EscrowDomainEventJpaEntity.builder()
                    .eventId(domainEvent.getEventId().toString())
                    .eventType(domainEvent.getEventType())
                    .aggregateId(account.getEscrowId().value())
                    .payload(payload)
                    .status(EscrowDomainEventJpaEntity.Status.PENDING)
                    .build();

            escrowDomainEventJpaRepository.save(event);
            log.debug("Outbox event written: {} ({}) for escrow {}", event.getEventId(), event.getEventType(), event.getAggregateId());
        }
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EscrowAccount> findById(EscrowId escrowId) {
        return jpaRepo.findByEscrowId(escrowId.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EscrowAccount> findByContractId(String contractId) {
        return jpaRepo.findByContractId(contractId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByContractId(String contractId) {
        return jpaRepo.existsByContractId(contractId);
    }
}
