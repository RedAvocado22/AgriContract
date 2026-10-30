package com.agricontract.product.application.usecase;

import com.agricontract.product.common.exception.InventoryInvariantViolationException;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RESERVED -> RELEASED plus the counter restore, shared by compensation release and the orphan reaper.
 * Must run inside the caller's transaction so status and counter commit together and the quantity is
 * restored exactly once. Seller-closed and PAUSED listings only get the counter back (see releaseAvailable).
 */
@Component
@RequiredArgsConstructor
class ReservationReleaser {

    private final ListingRepository listingRepository;
    private final ReservationRepository reservationRepository;

    /** @return false if the reservation was no longer RESERVED (another release or a transition won) */
    boolean releaseReserved(Reservation reservation) {
        if (!reservationRepository.transition(reservation.getContractId(),
                ReservationStatus.RESERVED, ReservationStatus.RELEASED)) {
            return false;
        }
        if (!listingRepository.releaseAvailable(reservation.getListingId(), reservation.getReservedQuantity())) {
            throw new InventoryInvariantViolationException("Restoring " + reservation.getReservedQuantity()
                    + " to listing " + reservation.getListingId().value() + " would exceed its offered quantity");
        }
        return true;
    }
}
