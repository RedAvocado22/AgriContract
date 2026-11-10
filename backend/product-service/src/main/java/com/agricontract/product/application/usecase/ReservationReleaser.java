package com.agricontract.product.application.usecase;

import com.agricontract.product.common.exception.InventoryInvariantViolationException;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * -> RELEASED plus the counter restore, shared by compensation release, the orphan reaper and the withdrawn /
 * activation_failed consumers. Must run inside the caller's transaction so status and counter commit together and
 * the quantity is restored exactly once. Seller-closed and PAUSED listings only get the counter back (see
 * releaseAvailable).
 */
@Component
@RequiredArgsConstructor
class ReservationReleaser {

    private final ListingRepository listingRepository;
    private final ReservationRepository reservationRepository;

    /** @return false if the reservation was no longer RESERVED (another release or a transition won) */
    boolean releaseReserved(Reservation reservation) {
        return release(reservation, ReservationStatus.RESERVED);
    }

    /**
     * RESERVED or SIGNED_RELEASABLE -> RELEASED; never from COMMITTED.
     *
     * @return false if the reservation was no longer in {@code from} (another release or a transition won)
     */
    boolean release(Reservation reservation, ReservationStatus from) {
        if (!from.canTransitionTo(ReservationStatus.RELEASED)) {
            throw new IllegalArgumentException("A " + from + " reservation cannot be released");
        }
        if (!reservationRepository.transition(reservation.getContractId(), from, ReservationStatus.RELEASED)) {
            return false;
        }
        if (!listingRepository.releaseAvailable(reservation.getListingId(), reservation.getReservedQuantity())) {
            throw new InventoryInvariantViolationException("Restoring " + reservation.getReservedQuantity()
                    + " to listing " + reservation.getListingId().value() + " would exceed its offered quantity");
        }
        return true;
    }
}
