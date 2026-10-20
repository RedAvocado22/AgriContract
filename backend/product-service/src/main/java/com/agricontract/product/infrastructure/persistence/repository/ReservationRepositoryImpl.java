package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    @Transactional
    public boolean transition(ContractId contractId, ReservationStatus expected, ReservationStatus target) {
        if (!expected.canTransitionTo(target)) {
            throw new IllegalArgumentException("Illegal reservation transition " + expected + " -> " + target);
        }
        return reservationJpaRepository.transition(contractId.value(), expected, target, LocalDateTime.now()) == 1;
    }
}
