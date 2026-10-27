package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ReleaseListingReservationRequest;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.common.exception.ReservationNotFoundException;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import static com.agricontract.product.common.exception.ReservationConflictException.RESERVATION_STATE_CONFLICT;

/**
 * Compensation when contract-service reserved but could not persist OFFERED.
 * Idempotent: an already RELEASED reservation succeeds without restoring the quantity twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseListingReservationUseCaseImpl implements ReleaseListingReservationUseCase {

    private final ReservationRepository reservationRepository;
    private final ReservationReleaser reservationReleaser;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void execute(String contractIdValue, ReleaseListingReservationRequest request) {
        ContractId contractId = new ContractId(contractIdValue);
        Reservation reservation = load(contractId);

        if (!reservation.isSameRequest(new ListingId(request.listingId()), request.reservedQuantity())) {
            throw new ReservationConflictException(RESERVATION_STATE_CONFLICT,
                    "Compensation payload does not match reservation for contract " + contractId.value());
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw notCompensable(contractId, reservation.getStatus());
        }

        boolean released = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> reservationReleaser.releaseReserved(reservation)));
        if (released) {
            log.info("Released reservation for contract {} on listing {} ({})",
                    contractId.value(), reservation.getListingId().value(), reservation.getReservedQuantity());
            return;
        }

        // lost a race: another release won (fine) or the contract moved on (conflict)
        ReservationStatus current = load(contractId).getStatus();
        if (current != ReservationStatus.RELEASED) {
            throw notCompensable(contractId, current);
        }
    }

    private Reservation load(ContractId contractId) {
        return reservationRepository.findByContractId(contractId)
                .orElseThrow(() -> new ReservationNotFoundException(contractId.value()));
    }

    private static ReservationConflictException notCompensable(ContractId contractId, ReservationStatus status) {
        return new ReservationConflictException(RESERVATION_STATE_CONFLICT,
                "Reservation for contract " + contractId.value() + " is " + status + " and cannot be compensated");
    }
}
