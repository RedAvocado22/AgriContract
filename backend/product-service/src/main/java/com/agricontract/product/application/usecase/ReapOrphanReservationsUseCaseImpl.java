package com.agricontract.product.application.usecase;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
import com.agricontract.product.domain.model.vo.OrphanCandidate;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ProcessedContractEventRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orphan reservation reaper (design §8b.2, matrix 28c): releases a reservation that is still RESERVED past the
 * grace period with no contract.created recorded — contract-service reserved but never persisted the contract and
 * the compensation release never arrived. Local data only; SIGNED_RELEASABLE and COMMITTED are never touched.
 * Each run pages through every candidate, so rows that keep failing cannot starve the orphans behind them.
 */
@Slf4j
@Service
public class ReapOrphanReservationsUseCaseImpl implements ReapOrphanReservationsUseCase {

    private final ReservationRepository reservationRepository;
    private final ProcessedContractEventRepository processedContractEventRepository;
    private final ReservationReleaser reservationReleaser;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public ReapOrphanReservationsUseCaseImpl(ReservationRepository reservationRepository,
                                             ProcessedContractEventRepository processedContractEventRepository,
                                             ReservationReleaser reservationReleaser,
                                             TransactionTemplate transactionTemplate,
                                             @Value("${reservation.orphan-reaper-batch-size:100}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("reservation.orphan-reaper-batch-size must be at least 1");
        }
        this.reservationRepository = reservationRepository;
        this.processedContractEventRepository = processedContractEventRepository;
        this.reservationReleaser = reservationReleaser;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Override
    public int execute(Instant createdBefore) {
        int released = 0;
        OrphanCandidate after = null;
        List<OrphanCandidate> page;
        do {
            page = reservationRepository.findOrphanCandidates(createdBefore, after, batchSize);
            for (OrphanCandidate candidate : page) {
                if (reap(candidate.contractId())) {
                    released++;
                }
                after = candidate;
            }
        } while (page.size() == batchSize);
        return released;
    }

    private boolean reap(ContractId contractId) {
        try {
            Reservation reaped = transactionTemplate.execute(status -> reapIfStillOrphan(contractId));
            if (reaped == null) {
                return false;
            }
            log.warn("event=orphan_reservation_released alert=OPERATOR contractId={} listingId={} quantity={}",
                    contractId.value(), reaped.getListingId().value(), reaped.getReservedQuantity());
            return true;
        } catch (RuntimeException e) {
            // one bad row must not stop the rest of the run; it stays RESERVED and is retried next run
            log.error("event=orphan_reservation_reap_failed alert=OPERATOR contractId={}", contractId.value(), e);
            return false;
        }
    }

    // Re-checked under the reservation row lock: the candidate query ran without it, so contract.created may have
    // been recorded, or the reservation moved on, in between. The consumer takes the same lock first.
    private Reservation reapIfStillOrphan(ContractId contractId) {
        Optional<Reservation> locked = reservationRepository.lockByContractId(contractId);
        if (locked.isEmpty() || locked.get().getStatus() != ReservationStatus.RESERVED) {
            return null;
        }
        if (processedContractEventRepository.hasRecorded(contractId, ContractLifecycleEventType.CONTRACT_CREATED)) {
            return null;
        }
        return reservationReleaser.releaseReserved(locked.get()) ? locked.get() : null;
    }
}
