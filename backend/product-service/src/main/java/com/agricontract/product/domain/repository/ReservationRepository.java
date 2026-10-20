package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ReservationStatus;

import java.util.Optional;

public interface ReservationRepository {

    /**
     * Insert only — a second reservation for the same contractId violates the unique key
     * and surfaces as DataIntegrityViolationException.
     */
    Reservation create(Reservation reservation);

    Optional<Reservation> findByContractId(ContractId contractId);

    /**
     * Conditional transition: applies only while the row is still in {@code expected}, so replayed
     * events or concurrent workers move a reservation (and restore inventory) at most once.
     *
     * @return true if this call performed the transition
     */
    boolean transition(ContractId contractId, ReservationStatus expected, ReservationStatus target);
}
