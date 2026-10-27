package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {

    /**
     * Insert only — a second reservation for the same contractId violates the unique key
     * and surfaces as DataIntegrityViolationException.
     */
    Reservation create(Reservation reservation);

    Optional<Reservation> findByContractId(ContractId contractId);

    /**
     * Reads and row-locks the reservation until the caller's transaction ends. The orphan reaper and the
     * contract.created consumer both lock it first, so they never decide on the same reservation concurrently.
     */
    Optional<Reservation> lockByContractId(ContractId contractId);

    /**
     * Orphan candidates (design §8b.2): still RESERVED, created before {@code createdBefore}, and no
     * contract.created recorded in the dedup store. Oldest first, at most {@code limit}.
     */
    List<ContractId> findOrphanCandidates(Instant createdBefore, int limit);

    /**
     * Conditional transition: applies only while the row is still in {@code expected}, so replayed
     * events or concurrent workers move a reservation (and restore inventory) at most once.
     *
     * @return true if this call performed the transition
     */
    boolean transition(ContractId contractId, ReservationStatus expected, ReservationStatus target);
}
