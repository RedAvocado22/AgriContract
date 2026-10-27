package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {
    private final ReservationMapper reservationMapper;
    private final ReservationJpaRepository reservationJpaRepository;

    @Override
    @Transactional
    public Reservation create(Reservation reservation) {
        // saveAndFlush so a duplicate contractId fails here, inside the caller's transaction
        return reservationMapper.toDomain(
                reservationJpaRepository.saveAndFlush(reservationMapper.toJpaEntity(reservation)));
    }

    @Override
    public Optional<Reservation> findByContractId(ContractId contractId) {
        return reservationJpaRepository.findByContractId(contractId.value())
                .map(reservationMapper::toDomain);
    }

    @Override
    public Optional<Reservation> lockByContractId(ContractId contractId) {
        return reservationJpaRepository.lockByContractId(contractId.value())
                .map(reservationMapper::toDomain);
    }

    @Override
    public List<ContractId> findOrphanCandidates(Instant createdBefore, int limit) {
        // created_at is a JVM-local LocalDateTime (@CreationTimestamp), so compare in the same zone
        LocalDateTime cutoff = LocalDateTime.ofInstant(createdBefore, ZoneId.systemDefault());
        return reservationJpaRepository.findOrphanCandidates(cutoff, Limit.of(limit)).stream()
                .map(ContractId::new)
                .toList();
    }

    @Override
    @Transactional
    public boolean transition(ContractId contractId, ReservationStatus expected, ReservationStatus target) {
        if (!expected.canTransitionTo(target)) {
            throw new IllegalArgumentException("Illegal reservation transition " + expected + " -> " + target);
        }
        return reservationJpaRepository.transition(contractId.value(), expected, target, LocalDateTime.now()) == 1;
    }
}
