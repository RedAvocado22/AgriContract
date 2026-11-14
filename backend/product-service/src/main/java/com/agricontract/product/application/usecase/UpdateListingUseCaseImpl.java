package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.port.TransactionRunner;
import com.agricontract.product.common.exception.IdempotencyKeyReusedException;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.common.exception.NotListingSellerException;
import com.agricontract.product.common.exception.OfferedQuantityBelowReservedException;
import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ListingEditKeyRepository;
import com.agricontract.product.domain.repository.ListingEditKeyRepository.EditKey;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * updateListing (contract, product design §4 UpdateListing, §8b.1, D48): the owning seller edits or republishes a
 * listing with the same body as create.
 * <p>
 * The listing row is locked first and the reserved total is read after it in the same transaction, so a concurrent
 * reserve or release either finished before the edit (and is counted) or runs after it: a reserve then fails on the
 * new version token, a release adds back to the counter the edit just set. quantityAvailable therefore always equals
 * offeredQuantity minus what is still reserved.
 */
@Service
@RequiredArgsConstructor
public class UpdateListingUseCaseImpl implements UpdateListingUseCase {

    private final ListingRepository listingRepository;
    private final ReservationRepository reservationRepository;
    private final ListingEditKeyRepository editKeyRepository;
    private final ListingPlotPicker plotPicker;
    private final SellerTradingGate tradingGate;
    private final TransactionRunner transactionRunner;

    @Override
    public ListingResponse execute(String sellerId, String listingId, String idempotencyKey,
                                   CreateListingRequest request) {
        ListingRequests.requireIdempotencyKey(idempotencyKey);
        tradingGate.assertEligible(sellerId);
        ListingId id = new ListingId(listingId);
        String fingerprint = ListingRequests.fingerprint(request);

        try {
            return transactionRunner.inTransaction(() -> editLocked(sellerId, id, idempotencyKey, request, fingerprint));
        } catch (WriteConflictException e) {
            // a concurrent request with the same key committed first; this one changed nothing
            return transactionRunner.inTransaction(() -> {
                Listing listing = listingRepository.findById(id).orElseThrow(() -> e);
                return replayOf(sellerId, idempotencyKey, id, fingerprint, listing).orElseThrow(() -> e);
            });
        }
    }

    private ListingResponse editLocked(String sellerId, ListingId id, String idempotencyKey,
                                       CreateListingRequest request, String fingerprint) {
        // the lock is the transaction's first read, so every read below sees what committed before it
        Listing listing = listingRepository.lockForEdit(id)
                .orElseThrow(() -> new ListingNotFoundException(id.value()));
        if (!listing.isOwnedBy(sellerId)) {
            throw new NotListingSellerException();
        }
        Optional<ListingResponse> replayed = replayOf(sellerId, idempotencyKey, id, fingerprint, listing);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        if (!listing.getProductId().equals(new ProductId(request.productId()))) {
            throw new IllegalArgumentException("productId of a listing cannot change");
        }
        if (!listing.isEditable()) {
            // design §8b: other commodity or no quality spec stays readable, never becomes a Phase 2 listing
            throw new IllegalArgumentException(
                    "A legacy listing without a coffee quality spec stays readable but cannot be edited");
        }
        ListingRequests.requireSameProductName(listing.getProductName(), request);
        List<ListingPlot> plots = plotPicker.pick(sellerId, request.registryEntryIds());

        BigDecimal reserved = reservationRepository.sumUnreleasedQuantity(id);
        if (request.offeredQuantity().compareTo(reserved) < 0) {
            throw new OfferedQuantityBelowReservedException(reserved);
        }
        listing.edit(request.offeredQuantity(), request.minOrderQuantity(), request.askingUnitPriceValue(), plots,
                ListingRequests.goods(listing.getGoods(), request), reserved);

        Listing saved = listingRepository.saveEdit(listing);
        editKeyRepository.record(sellerId, idempotencyKey, new EditKey(id, fingerprint));
        return ListingResponse.from(saved);
    }

    /**
     * An edit this seller already applied with this key: the listing as it is now, with no second change. The same
     * key for another listing or another body is 409.
     */
    private Optional<ListingResponse> replayOf(String sellerId, String idempotencyKey, ListingId id,
                                               String fingerprint, Listing listing) {
        Optional<EditKey> applied = editKeyRepository.find(sellerId, idempotencyKey);
        if (applied.isEmpty()) {
            return Optional.empty();
        }
        if (applied.get().listingId().equals(id) && applied.get().requestFingerprint().equals(fingerprint)) {
            return Optional.of(ListingResponse.from(listing));
        }
        throw new IdempotencyKeyReusedException();
    }
}
