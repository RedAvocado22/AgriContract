package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingReservationResponse;
import com.agricontract.product.application.dto.ReservationResult;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ReservationRejection;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static com.agricontract.product.common.exception.ReservationConflictException.*;

/**
 * Called synchronously by contract-service before it persists an OFFERED contract.
 * contractId is the idempotency identity: an identical replay returns the original reservation
 * without decrementing again.
 */
@Service
@RequiredArgsConstructor
public class ReserveListingQuantityUseCaseImpl implements ReserveListingQuantityUseCase {

    private final ListingRepository listingRepository;
    private final ReservationRepository reservationRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ReservationResult execute(ReserveListingQuantityRequest request) {
        ContractId contractId = new ContractId(request.contractId());
        ListingId listingId = new ListingId(request.listingId());
        BigDecimal quantity = request.requestedQuantity();

        Optional<ReservationResult> replayed = replay(contractId, listingId, quantity);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        try {
            ListingReservationResponse reserved = transactionTemplate.execute(status ->
                    reserveAtomically(contractId, listingId, quantity, request.listingVersionToken()));
            return new ReservationResult(reserved, true);
        } catch (DataIntegrityViolationException e) {
            // a concurrent reserve with the same contractId committed first; our decrement was rolled back
            return replay(contractId, listingId, quantity).orElseThrow(() -> e);
        } catch (ReserveRejected e) {
            // classify outside the rolled-back transaction so it sees the latest committed listing
            Listing listing = load(listingId);
            throw toConflict(listing.classifyRejectedReserve(quantity, request.listingVersionToken()), listingId);
        }
    }

    // Conditional decrement and reservation insert commit together or not at all.
    // Decrement first: it takes the listing row's exclusive lock before the insert's FK check needs a
    // shared lock on it — the reverse order deadlocks two buyers racing for the same listing.
    private ListingReservationResponse reserveAtomically(ContractId contractId, ListingId listingId,
                                                         BigDecimal quantity, String versionToken) {
        load(listingId);
        if (!listingRepository.reserveAvailable(listingId, quantity, versionToken)) {
            throw new ReserveRejected();
        }
        // a duplicate contractId fails here and rolls the decrement back
        Reservation reservation = reservationRepository.create(Reservation.create(contractId, listingId, quantity));
        return ListingReservationResponse.from(reservation, load(listingId).getQuantityAvailable());
    }

    private Optional<ReservationResult> replay(ContractId contractId, ListingId listingId, BigDecimal quantity) {
        return reservationRepository.findByContractId(contractId).map(existing -> {
            if (!existing.isSameRequest(listingId, quantity)) {
                throw new ReservationConflictException(RESERVATION_STATE_CONFLICT,
                        "Reservation for contract " + contractId.value() + " exists with a different listing or quantity");
            }
            if (existing.getStatus() == ReservationStatus.RELEASED) {
                // the quantity is back on the listing; reviving it would let a contract hold unreserved goods
                throw new ReservationConflictException(RESERVATION_STATE_CONFLICT,
                        "Reservation for contract " + contractId.value() + " was already released");
            }
            return new ReservationResult(
                    ListingReservationResponse.from(existing, load(listingId).getQuantityAvailable()), false);
        });
    }

    private Listing load(ListingId listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.value()));
    }

    private static ReservationConflictException toConflict(ReservationRejection rejection, ListingId listingId) {
        return switch (rejection) {
            case INSUFFICIENT_QUANTITY -> new ReservationConflictException(INSUFFICIENT_LISTING_QUANTITY,
                    "Listing " + listingId.value() + " cannot cover the requested quantity");
            case LISTING_STATE_CONFLICT -> new ReservationConflictException(LISTING_STATE_CONFLICT,
                    "Listing " + listingId.value() + " is not open for reservation");
            case VERSION_MISMATCH -> new ReservationConflictException(LISTING_VERSION_MISMATCH,
                    "Listing " + listingId.value() + " has changed; reload it and retry with the new version token");
        };
    }

    // Signals that the conditional decrement matched no row; nothing was written.
    private static class ReserveRejected extends RuntimeException {
        ReserveRejected() {
            super(null, null, false, false);
        }
    }
}
