package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.OrphanCandidate;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    // MySQL DATETIME minimum: sorts before every row, so the first page needs no separate query
    private static final LocalDateTime SCAN_START = LocalDateTime.of(1000, 1, 1, 0, 0);

    private final ReservationMapper reservationMapper;
    private final ReservationJpaRepository reservationJpaRepository;

    @Override
    @Transactional
    public Reservation create(Reservation reservation) {
        // saveAndFlush so a duplicate contractId fails here, inside the caller's transaction
        try {
            return reservationMapper.toDomain(
                    reservationJpaRepository.saveAndFlush(reservationMapper.toJpaEntity(reservation)));
        } catch (DataIntegrityViolationException e) {
            throw new WriteConflictException("Reservation for contract " + reservation.getContractId().value()
                    + " could not be written", e);
        }
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
    public List<OrphanCandidate> findOrphanCandidates(Instant createdBefore, OrphanCandidate after, int limit) {
        // created_at is a JVM-local LocalDateTime (@CreationTimestamp), so compare in the same zone
        LocalDateTime cutoff = LocalDateTime.ofInstant(createdBefore, ZoneId.systemDefault());
        LocalDateTime afterCreatedAt = after == null ? SCAN_START : after.createdAt();
        String afterContractId = after == null ? "" : after.contractId().value();
        return reservationJpaRepository
                .findOrphanCandidates(cutoff, afterCreatedAt, afterContractId, Limit.of(limit)).stream()
                .map(row -> new OrphanCandidate(new ContractId(row.getContractId()), row.getCreatedAt()))
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
