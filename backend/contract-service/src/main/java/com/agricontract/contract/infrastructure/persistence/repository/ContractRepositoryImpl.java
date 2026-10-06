package com.agricontract.contract.infrastructure.persistence.repository;

import com.agricontract.contract.domain.event.DomainEvent;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.infrastructure.persistence.entity.ContractDomainEventJpaEntity;
import com.agricontract.contract.infrastructure.persistence.entity.ContractJpaEntity;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ContractRepositoryImpl implements ContractRepository {

    private final ContractJpaRepository jpaRepo;
    private final ContractDomainEventJpaRepository eventRepo;
    private final ContractMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Contract save(Contract contract) {
        var entity = mapper.toJpaEntity(contract);
        jpaRepo.findByContractId(contract.getContractId().value())
               .ifPresent(existing -> {
                   entity.setId(existing.getId());
                   entity.setVersion(existing.getVersion());
               });
        jpaRepo.save(entity);

        List<DomainEvent> events = contract.pullDomainEvents();
        events.stream()
              .map(this::toOutboxEntity)
              .forEach(eventRepo::save);

        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Contract> findById(ContractId contractId) {
        return jpaRepo.findByContractId(contractId.value())
                      .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> findByBuyerId(String buyerId) {
        return jpaRepo.findByBuyerId(buyerId).stream()
                      .map(mapper::toDomain)
                      .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> findBySellerId(String sellerId) {
        return jpaRepo.findBySellerId(sellerId).stream()
                      .map(mapper::toDomain)
                      .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> findByStatus(ContractStatus status) {
        return jpaRepo.findByStatus(status).stream()
                      .map(mapper::toDomain)
                      .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> findByBuyerId(String buyerId, ContractStatus status, Pageable pageable) {
        Page<ContractJpaEntity> page = status != null
                ? jpaRepo.findByBuyerIdAndStatus(buyerId, status, pageable)
                : jpaRepo.findByBuyerId(buyerId, pageable);
        return page.getContent().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> findBySellerId(String sellerId, ContractStatus status, Pageable pageable) {
        Page<ContractJpaEntity> page = status != null
                ? jpaRepo.findBySellerIdAndStatus(sellerId, status, pageable)
                : jpaRepo.findBySellerId(sellerId, pageable);
        return page.getContent().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByBuyerId(String buyerId, ContractStatus status) {
        return status != null
                ? jpaRepo.countByBuyerIdAndStatus(buyerId, status)
                : jpaRepo.countByBuyerId(buyerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countBySellerId(String sellerId, ContractStatus status) {
        return status != null
                ? jpaRepo.countBySellerIdAndStatus(sellerId, status)
                : jpaRepo.countBySellerId(sellerId);
    }

    private ContractDomainEventJpaEntity toOutboxEntity(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return ContractDomainEventJpaEntity.builder()
                    .eventId(event.getEventId().toString())
                    .eventType(event.getEventType())
                    .aggregateId(event.getContractId())
                    .payload(payload)
                    .status(ContractDomainEventJpaEntity.Status.PENDING)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize domain event: " + event.getEventType(), ex);
        }
    }
}
